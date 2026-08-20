package com.localexpense.tracker.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.data.Account
import com.localexpense.tracker.data.AppSettings
import com.localexpense.tracker.data.BackupManager
import com.localexpense.tracker.data.Budget
import com.localexpense.tracker.data.Category
import com.localexpense.tracker.data.CategoryTotal
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.data.Merchant
import com.localexpense.tracker.data.MerchantRule
import com.localexpense.tracker.data.PeriodBankTotal
import com.localexpense.tracker.data.PeriodTotal
import com.localexpense.tracker.data.RecurringExpense
import com.localexpense.tracker.data.SourceTotal
import com.localexpense.tracker.data.TransactionFilter
import com.localexpense.tracker.data.TransactionSource
import com.localexpense.tracker.data.TransactionType
import com.localexpense.tracker.domain.isAnomalous
import com.localexpense.tracker.money.formatMinor
import com.localexpense.tracker.parser.SmsImporter
import com.localexpense.tracker.parser.SmsParser
import com.localexpense.tracker.util.ExportSink
import com.localexpense.tracker.util.monthRange
import com.localexpense.tracker.util.parseAmountMinor
import com.localexpense.tracker.util.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.localexpense.tracker.data.SmsRule

sealed class CleanupState {
    data object Idle : CleanupState()
    data object Running : CleanupState()
    data class Done(val removed: Int) : CleanupState()
}

data class RuleTestResult(
    val matched: Boolean,
    val amountMinor: Long? = null,
    val merchant: String? = null,
    val bankName: String? = null
)

data class SmsTestResult(
    val matched: Boolean,
    val amountMinor: Long? = null,
    val merchant: String? = null,
    val bankName: String? = null,
    val categoryName: String? = null
)

sealed class ImportState {
    data object Idle : ImportState()
    data object Running : ImportState()
    data class Done(val scanned: Int, val imported: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}

/** حالة النسخ الاحتياطي/الاسترجاع (المرحلة 3 من خطة الترحيل). */
sealed class BackupState {
    data object Idle : BackupState()
    data object Running : BackupState()
    data class Done(val message: String) : BackupState()
    data class Error(val message: String) : BackupState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(application)
    val settings = AppSettings(application)

    val expenses: StateFlow<List<Expense>> = repository.observeExpenses()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.expenses", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransactions: StateFlow<List<Expense>> = repository.observeRecent(10)
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.recentTransactions", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentMonth: Pair<Long, Long> get() = monthRange().let { it.start to it.end }

    val monthTotalsBySource: StateFlow<List<SourceTotal>> = repository.observeTotalsBySource()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.monthTotalsBySource", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthTotalsByCategory: StateFlow<List<CategoryTotal>> = currentMonth.let { (s, e) ->
        repository.observeTotalsByCategoryBetween(s, e)
    }
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.monthTotalsByCategory", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthTotal: StateFlow<Long> = monthTotalsByCategory
        .map { list -> list.sumOf { it.total } }
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.monthTotal", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.categories", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<Account>> = repository.observeAccounts()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.accounts", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val merchants: StateFlow<List<Merchant>> = repository.observeMerchants()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.merchants", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val merchantRules: StateFlow<List<MerchantRule>> = repository.observeMerchantRules()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.merchantRules", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankNames: StateFlow<List<String>> = repository.observeBankNames()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.bankNames", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // شجرة الشاشة الرئيسية (سنة -> شهر -> بنك) بتتبني من تجميعات SQL: صفوف
    // قليلة بدل ما نحمّل كل الحركات ونجمعها في الـ Compose.
    val monthTotals: StateFlow<List<PeriodTotal>> = repository.observeMonthTotals()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.monthTotals", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthBankTotals: StateFlow<List<PeriodBankTotal>> = repository.observeMonthBankTotals()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.monthBankTotals", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** حركات مجموعة واحدة (شهر + بنك) — بتتحمّل بس لما المستخدم يفتحها. */
    fun observeGroupTransactions(month: String, bankName: String): Flow<List<Expense>> =
        repository.observeMonthBankTransactions(month, bankName)

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _cleanupState = MutableStateFlow<CleanupState>(CleanupState.Idle)
    val cleanupState: StateFlow<CleanupState> = _cleanupState.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    /** تحذير حركة شاذة (المرحلة 11) — بيظهر كبطاقة بعد تسجيل عملية غير معتادة. */
    private val _anomalyWarning = MutableStateFlow<String?>(null)
    val anomalyWarning: StateFlow<String?> = _anomalyWarning.asStateFlow()

    val rules: StateFlow<List<SmsRule>> = repository.observeRules()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.rules", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<Budget>> = repository.observeBudgets()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.budgets", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overallBudget: StateFlow<Long> = repository.observeOverallBudget()
        .map { it ?: 0L }
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.overallBudget", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val recurringExpenses: StateFlow<List<RecurringExpense>> = repository.observeRecurringExpenses()
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.recurringExpenses", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ===== البحث والفلاتر (المرحلة 3) =====

    private val _filter = MutableStateFlow(TransactionFilter())
    val filter: StateFlow<TransactionFilter> = _filter.asStateFlow()

    val searchResults: StateFlow<List<Expense>> = _filter
        .flatMapLatest { repository.search(it) }
        .catch { CrashLog.recordNonFatal(getApplication<Application>(), "MainViewModel.searchResults", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateFilter(transform: (TransactionFilter) -> TransactionFilter) {
        _filter.value = transform(_filter.value)
    }

    fun clearFilter() {
        _filter.value = TransactionFilter()
    }

    fun observeTransaction(id: Long): Flow<Expense?> = repository.observeTransaction(id)

    private val prefs = application.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)
    private val _archivedYears = MutableStateFlow<Set<String>>(
        prefs.getStringSet("archived_years", emptySet()) ?: emptySet()
    )
    val archivedYears: StateFlow<Set<String>> = _archivedYears.asStateFlow()

    init {
        checkRecurringExpenses()
    }

    /**
     * تسجيل الدفعات الدورية المستحقة عند فتح التطبيق (المنطق نفسه بقى في
     * الريبو عشان يدعم كل أنواع التكرار مش الشهري بس).
     */
    private fun checkRecurringExpenses() {
        launchLogging("MainViewModel.checkRecurringExpenses") {
            val inserted = withContext(Dispatchers.IO) { repository.processDueRecurring() }
            for (expense in inserted) {
                checkBudgetAlerts(expense.categoryName, expense.timestamp)
            }
            scheduleReminders()
        }
    }

    /**
     * جدولة الفحص اليومي لتنبيهات الدفعات القادمة. الفحص نفسه بيحصل في
     * PaymentReminderReceiver فبيشتغل كمان والتطبيق مقفول.
     */
    private fun scheduleReminders() {
        com.localexpense.tracker.notification.PaymentReminderScheduler.schedule(getApplication())
    }

    fun archiveYears(years: Set<String>) {
        val current = _archivedYears.value.toMutableSet()
        current.addAll(years)
        prefs.edit().putStringSet("archived_years", current).apply()
        _archivedYears.value = current
    }

    fun unarchiveYear(year: String) {
        val current = _archivedYears.value.toMutableSet()
        current.remove(year)
        prefs.edit().putStringSet("archived_years", current).apply()
        _archivedYears.value = current
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repository.addCategory(trimmed) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }

    // ===== إضافة وتعديل الحركات =====

    fun addManualExpense(amountMinor: Long, merchant: String, category: String = "عام") {
        addTransaction(amountMinor, merchant, category, TransactionType.EXPENSE)
    }

    fun saveExpense(amountMinor: Long, merchant: String, category: String) {
        addManualExpense(amountMinor, merchant, category)
    }

    /**
     * إضافة حركة يدوية من أي نوع (مصروف/دخل/استرداد). التحويلات ليها
     * [addTransfer] لأنها محتاجة حسابين.
     */
    fun addTransaction(
        amountMinor: Long,
        merchant: String,
        category: String = "عام",
        type: TransactionType = TransactionType.EXPENSE,
        accountId: Long? = null,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                Expense(
                    amountMinor = amountMinor,
                    type = type,
                    merchant = merchant,
                    bankName = if (type == TransactionType.INCOME) "دخل" else "يدوي",
                    timestamp = timestamp,
                    rawBody = "",
                    categoryName = category,
                    accountId = accountId,
                    source = TransactionSource.MANUAL,
                    note = note
                ),
                autoCategorize = true
            )

            if (type == TransactionType.EXPENSE) {
                checkAnomaly(amountMinor, category)
                checkBudgetAlerts(category, timestamp)
            }
        }
    }

    fun addTransfer(amountMinor: Long, fromAccountId: Long?, toAccountId: Long?, note: String = "") {
        viewModelScope.launch { repository.addTransfer(amountMinor, fromAccountId, toAccountId, note) }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { repository.updateExpense(expense) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    fun setVerified(expense: Expense, verified: Boolean) {
        viewModelScope.launch { repository.setVerified(expense, verified) }
    }

    fun updateNote(expense: Expense, note: String) {
        viewModelScope.launch { repository.updateExpense(expense.copy(note = note)) }
    }

    /**
     * تغيير فئة حركة. [applyToMerchant] = true معناها المستخدم طلب صريح إن كل
     * حركات نفس الجهة تتحول للفئة دي وإن القاعدة تتحفظ للمستقبل (المرحلة 4).
     */
    fun changeCategory(expense: Expense, categoryName: String, applyToMerchant: Boolean) {
        viewModelScope.launch {
            repository.updateExpense(expense.copy(categoryName = categoryName))
            if (applyToMerchant) {
                repository.learnMerchantCategory(expense.merchant, categoryName, applyToPast = true)
            }
        }
    }

    fun changeMerchant(expense: Expense, merchant: String) {
        viewModelScope.launch { repository.updateExpense(expense.copy(merchant = merchant)) }
    }

    /** "اعمل قاعدة من الحركة دي" (المرحلة 5، بند 19). */
    fun createRuleFromTransaction(expense: Expense) {
        viewModelScope.launch {
            repository.learnMerchantCategory(expense.merchant, expense.categoryName, applyToPast = false)
        }
    }

    private suspend fun checkAnomaly(amountMinor: Long, category: String) {
        val since = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000 // آخر 6 شهور
        val (average, count) = repository.categoryAverageAndCount(category, since)
        val anomalous = isAnomalous(
            amountMinor = amountMinor,
            categoryAverageMinor = average,
            categorySampleCount = count,
            thresholdMultiplier = settings.anomalyMultiplier.toDouble()
        )
        _anomalyWarning.value = if (anomalous && average != null) {
            "العملية دي (${formatMinor(amountMinor)}) أعلى بكتير من متوسط \"$category\" " +
                "(${formatMinor(average.toLong())})."
        } else {
            null
        }
    }

    fun dismissAnomalyWarning() {
        _anomalyWarning.value = null
    }

    private suspend fun checkBudgetAlerts(category: String, timestamp: Long) {
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val db = com.localexpense.tracker.data.AppDatabase.getDatabase(context)
            com.localexpense.tracker.notification.BudgetAlertChecker.checkAndNotify(
                context, db.expenseDao(), db.budgetDao(), category, timestamp
            )
        }
    }

    // ===== الحسابات =====

    fun saveAccount(account: Account) {
        viewModelScope.launch { repository.saveAccount(account) }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { repository.deleteAccount(account) }
    }

    suspend fun accountBalance(accountId: Long): Long = repository.accountBalance(accountId)

    // ===== الجهات =====

    suspend fun merchantAnalytics(merchant: String) = repository.merchantAnalytics(merchant)

    suspend fun searchByMerchant(merchant: String): List<Expense> =
        repository.searchOnce(TransactionFilter(merchant = merchant, limit = 200))

    fun setMerchantCategory(merchantName: String, categoryName: String, applyToPast: Boolean) {
        viewModelScope.launch {
            repository.learnMerchantCategory(merchantName, categoryName, applyToPast)
        }
    }

    // ===== القواعد =====

    fun saveRule(rule: SmsRule) {
        viewModelScope.launch { repository.insertRule(rule) }
    }

    fun deleteRule(rule: SmsRule) {
        viewModelScope.launch { repository.deleteRule(rule) }
    }

    fun saveMerchantRule(rule: MerchantRule) {
        viewModelScope.launch { repository.saveMerchantRule(rule) }
    }

    fun deleteMerchantRule(rule: MerchantRule) {
        viewModelScope.launch { repository.deleteMerchantRule(rule) }
    }

    fun cleanupDuplicateExpenses() {
        viewModelScope.launch {
            _cleanupState.value = CleanupState.Running
            val removed = withContext(Dispatchers.IO) { repository.cleanupDuplicates() }
            _cleanupState.value = CleanupState.Done(removed)
        }
    }

    // ===== الميزانيات =====

    fun setBudget(categoryName: String, limitMinor: Long) {
        viewModelScope.launch { repository.setBudget(Budget(categoryName, limitMinor)) }
    }

    fun deleteBudget(categoryName: String) {
        viewModelScope.launch { repository.deleteBudget(categoryName) }
    }

    fun setOverallBudget(limitMinor: Long) {
        viewModelScope.launch { repository.setOverallBudget(limitMinor) }
    }

    // ===== الدوريات (التفاصيل في PlansViewModel) =====

    fun addRecurringExpense(
        amountMinor: Long,
        merchant: String,
        bankName: String,
        categoryName: String,
        dayOfMonth: Int
    ) {
        viewModelScope.launch {
            repository.insertRecurringExpense(
                RecurringExpense(
                    amountMinor = amountMinor,
                    merchant = merchant,
                    bankName = bankName,
                    categoryName = categoryName,
                    dayOfMonth = dayOfMonth,
                    lastAddedMonth = "",
                    nextDueDate = com.localexpense.tracker.domain.firstDueDateForDayOfMonth(
                        System.currentTimeMillis(), dayOfMonth
                    )
                )
            )
        }
    }

    fun deleteRecurringExpense(recurringExpense: RecurringExpense) {
        viewModelScope.launch { repository.deleteRecurringExpense(recurringExpense) }
    }

    // ===== الاستيراد =====

    fun importFromInbox(startMillis: Long? = null, endMillis: Long? = null) {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            val (scanned, imported) = withContext(Dispatchers.IO) {
                SmsImporter.importAllSms(getApplication(), startMillis, endMillis)
            }
            _importState.value = ImportState.Done(scanned = scanned, imported = imported)
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    // ===== النسخ الاحتياطي والاسترجاع =====

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.Running
            _backupState.value = try {
                val rows = BackupManager.export(getApplication(), uri)
                settings.lastBackupAt = System.currentTimeMillis()
                BackupState.Done("تم حفظ نسخة احتياطية فيها $rows سجل")
            } catch (e: Exception) {
                BackupState.Error("فشل الحفظ: ${e.message}")
            }
        }
    }

    /**
     * نسخة احتياطية لمجلد التطبيق - بديل لما منتقي الملفات مش متاح على الجهاز.
     */
    fun exportBackupToAppFolder() {
        viewModelScope.launch {
            _backupState.value = BackupState.Running
            _backupState.value = try {
                val text = BackupManager.encodeToText(getApplication())
                val path = withContext(Dispatchers.IO) {
                    ExportSink.write(
                        getApplication(),
                        BackupManager.suggestedFileName(),
                        text.toByteArray(Charsets.UTF_8)
                    )
                }
                if (path != null) {
                    settings.lastBackupAt = System.currentTimeMillis()
                    BackupState.Done("منتقي الملفات مش متاح، فالنسخة اتحفظت في:\n$path")
                } else {
                    BackupState.Error("فشل الحفظ: مفيش مكان متاح للكتابة")
                }
            } catch (e: Exception) {
                BackupState.Error("فشل الحفظ: ${e.message ?: "خطأ غير متوقع"}")
            }
        }
    }

    /** الاسترجاع محتاج ملف يختاره المستخدم، فمفيش بديل تلقائي له. */
    fun reportPickerUnavailable() {
        _backupState.value = BackupState.Error(
            "منتقي الملفات مش متاح على الجهاز ده، فمش ممكن نختار ملف نسخة للاسترجاع."
        )
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.Running
            _backupState.value = when (val result = BackupManager.restore(getApplication(), uri)) {
                is BackupManager.RestoreResult.Success ->
                    BackupState.Done("تم استرجاع ${result.rows} سجل")
                is BackupManager.RestoreResult.Failure ->
                    BackupState.Error(result.message)
            }
        }
    }

    fun resetBackupState() {
        _backupState.value = BackupState.Idle
    }

    // ===== اختبار القواعد =====

    fun testSmsMessage(sender: String, body: String): SmsTestResult {
        val result = SmsParser.parseSms(sender, body, System.currentTimeMillis(), rules.value)
            ?: return SmsTestResult(matched = false)
        return SmsTestResult(
            matched = true,
            amountMinor = result.amountMinor,
            merchant = result.merchant,
            bankName = result.bankName,
            categoryName = result.categoryName
        )
    }

    fun testRule(sender: String, body: String, rule: SmsRule): RuleTestResult {
        try {
            val senderRegex = Regex(rule.senderPattern, RegexOption.IGNORE_CASE)
            if (rule.senderPattern.isNotBlank() && !senderRegex.containsMatchIn(sender)) return RuleTestResult(matched = false)

            val keywordRegex = Regex(rule.debitKeywordPattern, RegexOption.IGNORE_CASE)
            if (!keywordRegex.containsMatchIn(body)) return RuleTestResult(matched = false)

            val amountRegex = Regex(rule.amountPattern, RegexOption.IGNORE_CASE)
            val amountMatch = amountRegex.find(body)
            val amountMinor = amountMatch?.groupValues?.getOrNull(1)
                ?.let { parseAmountMinor(it) } ?: return RuleTestResult(matched = false)

            var merchant: String? = null
            if (rule.merchantPattern.isNotBlank()) {
                val merchantRegex = Regex(rule.merchantPattern, RegexOption.IGNORE_CASE)
                val merchantMatch = merchantRegex.find(body)
                merchant = merchantMatch?.groupValues?.getOrNull(1)?.trim()
            }

            return RuleTestResult(
                matched = true,
                amountMinor = amountMinor,
                merchant = merchant,
                bankName = rule.bankName
            )
        } catch (e: Exception) {
            return RuleTestResult(matched = false)
        }
    }

    /** مستخدمة في تصدير CSV من الواجهة. */
    val includeRawTextInExport: Boolean get() = settings.includeRawTextInExport

    fun setIncludeRawTextInExport(enabled: Boolean) {
        settings.includeRawTextInExport = enabled
    }
}
