package com.localexpense.tracker.data

import android.content.Context
import com.localexpense.tracker.domain.FinancialContext
import com.localexpense.tracker.domain.MonthSummary
import com.localexpense.tracker.domain.UpcomingKind
import com.localexpense.tracker.domain.UpcomingPayment
import com.localexpense.tracker.domain.categorize
import com.localexpense.tracker.domain.dailyAverageMinor
import com.localexpense.tracker.domain.firstDueDateForDayOfMonth
import com.localexpense.tracker.domain.forecast
import com.localexpense.tracker.domain.monthlyEquivalentMinor
import com.localexpense.tracker.domain.nextDueDate
import com.localexpense.tracker.domain.normalizeMerchant
import com.localexpense.tracker.util.TimeRange
import com.localexpense.tracker.util.dayOfMonth
import com.localexpense.tracker.util.daysInMonth
import com.localexpense.tracker.util.monthKey
import com.localexpense.tracker.util.monthLabel
import com.localexpense.tracker.util.monthRange
import com.localexpense.tracker.util.monthRangeOffset
import kotlinx.coroutines.flow.Flow

/**
 * طبقة البيانات الوحيدة اللي الـ ViewModels بتتكلم معاها. الاستعلامات كلها في
 * الـ DAOs، والحسابات المالية كلها في `domain/` (دوال Kotlin صافية)، والريبو
 * ده بيوصّل بينهم — نفس نمط UI -> ViewModel -> (domain) -> Repository -> DAO.
 */
class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val smsRuleDao: SmsRuleDao,
    private val budgetDao: BudgetDao,
    private val recurringExpenseDao: RecurringExpenseDao,
    private val accountDao: AccountDao,
    private val merchantDao: MerchantDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val installmentDao: InstallmentDao
) {

    constructor(context: Context) : this(
        AppDatabase.getDatabase(context).expenseDao(),
        AppDatabase.getDatabase(context).categoryDao(),
        AppDatabase.getDatabase(context).smsRuleDao(),
        AppDatabase.getDatabase(context).budgetDao(),
        AppDatabase.getDatabase(context).recurringExpenseDao(),
        AppDatabase.getDatabase(context).accountDao(),
        AppDatabase.getDatabase(context).merchantDao(),
        AppDatabase.getDatabase(context).merchantRuleDao(),
        AppDatabase.getDatabase(context).installmentDao()
    )

    // ===== الحركات =====

    fun observeAll(): Flow<List<Expense>> = expenseDao.observeAll()
    fun observeExpenses(): Flow<List<Expense>> = expenseDao.observeAll()
    fun observeRecent(limit: Int = 10): Flow<List<Expense>> = expenseDao.observeRecent(limit)
    fun observeTransaction(id: Long): Flow<Expense?> = expenseDao.observeById(id)
    fun observeBetween(start: Long, end: Long): Flow<List<Expense>> = expenseDao.observeBetween(start, end)
    fun observeBankNames(): Flow<List<String>> = expenseDao.observeBankNames()

    fun observeTotalsBySource(): Flow<List<SourceTotal>> = expenseDao.observeTotalsBySource()
    fun observeTotalsByCategory(): Flow<List<CategoryTotal>> = expenseDao.observeTotalsByCategory()
    fun observeTotalsByCategoryBetween(start: Long, end: Long): Flow<List<CategoryTotal>> =
        expenseDao.observeTotalsByCategoryBetween(start, end)
    fun observeDailyTotalsBetween(start: Long, end: Long): Flow<List<DayTotal>> =
        expenseDao.observeDailyTotalsBetween(start, end)
    fun observeDailyIncomeBetween(start: Long, end: Long): Flow<List<DayTotal>> =
        expenseDao.observeDailyIncomeBetween(start, end)

    fun observeMonthTotals(): Flow<List<PeriodTotal>> = expenseDao.observeMonthTotals()
    fun observeMonthBankTotals(): Flow<List<PeriodBankTotal>> = expenseDao.observeMonthBankTotals()
    fun observeMonthBankTransactions(month: String, bankName: String): Flow<List<Expense>> =
        expenseDao.observeMonthBankTransactions(month, bankName)
    fun observeTopMerchantsBetween(start: Long, end: Long, limit: Int = 5): Flow<List<MerchantTotal>> =
        expenseDao.observeTopMerchantsBetween(start, end, limit)

    /** البحث والفلاتر (المرحلة 3). */
    fun search(filter: TransactionFilter): Flow<List<Expense>> =
        expenseDao.searchRaw(TransactionQuery.build(filter))

    suspend fun searchOnce(filter: TransactionFilter): List<Expense> =
        expenseDao.searchRawOnce(TransactionQuery.build(filter))

    suspend fun getTransaction(id: Long): Expense? = expenseDao.getById(id)

    /**
     * إدراج حركة. بيحدّد الفئة بمحرّك التصنيف لو الفئة مش محددة صريح، وبيسجّل
     * الجهة في جدول merchants (عشان تحليلات الجهات والتعلّم يشتغلوا).
     */
    suspend fun insert(expense: Expense): Long = insertTransaction(expense)

    suspend fun insertTransaction(
        expense: Expense,
        autoCategorize: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        val merchant = upsertMerchant(expense.merchant)
        val category = if (autoCategorize) {
            categorize(
                merchant = expense.merchant,
                rules = merchantRuleDao.getEnabled(),
                explicitMerchantCategory = merchant?.categoryName?.takeIf { it.isNotBlank() },
                keywordCategory = expense.categoryName
            ).categoryName
        } else {
            expense.categoryName
        }

        return expenseDao.insertExpense(
            expense.copy(
                categoryName = category,
                merchantId = merchant?.id,
                createdAt = if (expense.createdAt == 0L) now else expense.createdAt,
                updatedAt = now
            )
        )
    }

    suspend fun insertExpense(expense: Expense): Long = insertTransaction(expense)

    /**
     * نقطة الدخول الوحيدة للحركات الملتقطة من رسالة SMS أو إشعار بنك.
     *
     * بتعمل تلات حاجات مع بعض عشان ما تتفرقش بين المسارات: فحص التكرار
     * (مرجع البنك، ثم بصمة النص، ثم النص+التوقيت، ثم المبلغ+البنك في نافذة
     * قصيرة)، تسجيل الجهة، وتطبيق محرّك التصنيف (قواعد الجهات لها الأولوية
     * على الكلمات المفتاحية اللي الـ parser طلّعها).
     *
     * بترجّع الحركة بشكلها النهائي (بعد التصنيف) لو اتسجلت، و null لو
     * اتجاهلت كتكرار — اللي بينادي محتاج الفئة النهائية عشان يفحص تنبيهات
     * الميزانية على الفئة الصح، مش على فئة الـ parser المبدئية.
     */
    suspend fun captureTransaction(
        expense: Expense,
        dedupWindowMillis: Long = 10 * 60 * 1000L
    ): Expense? {
        val merchant = upsertMerchant(expense.merchant)
        val category = categorize(
            merchant = expense.merchant,
            rules = merchantRuleDao.getEnabled(),
            explicitMerchantCategory = merchant?.categoryName?.takeIf { it.isNotBlank() },
            keywordCategory = expense.categoryName
        ).categoryName
        val now = System.currentTimeMillis()

        val enriched = expense.copy(
            categoryName = category,
            merchantId = merchant?.id,
            createdAt = now,
            updatedAt = now
        )
        return if (expenseDao.insertIfNotDuplicate(enriched, dedupWindowMillis)) enriched else null
    }

    suspend fun update(expense: Expense) = updateExpense(expense)

    suspend fun updateExpense(expense: Expense) =
        expenseDao.update(expense.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(expense: Expense) = expenseDao.delete(expense)
    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    suspend fun setVerified(expense: Expense, verified: Boolean) =
        updateExpense(expense.copy(isVerified = verified))

    /**
     * تعلّم الجهات (المرحلة 4): المستخدم قال "طلبات = مطاعم" وطلب تطبيقها على
     * الكل. بنسجّل قاعدة دائمة **وبنحدّث الحركات القديمة** — الاتنين بطلب صريح
     * منه، مفيش أي إعادة تصنيف صامتة.
     */
    suspend fun learnMerchantCategory(merchantName: String, categoryName: String, applyToPast: Boolean) {
        val normalized = normalizeMerchant(merchantName)
        if (normalized.isBlank() || categoryName.isBlank()) return

        val now = System.currentTimeMillis()
        val existing = merchantDao.getByNormalizedName(normalized)
        if (existing == null) {
            merchantDao.insert(
                Merchant(
                    name = merchantName,
                    normalizedName = normalized,
                    categoryName = categoryName,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            merchantDao.update(existing.copy(categoryName = categoryName, updatedAt = now))
        }

        merchantRuleDao.deleteByPattern(normalized)
        merchantRuleDao.insert(
            MerchantRule(
                pattern = normalized,
                categoryName = categoryName,
                priority = 100,      // قواعد المستخدم أعلى من أي قاعدة مولّدة
                createdAt = now
            )
        )

        if (applyToPast) {
            expenseDao.setCategoryForMerchant(merchantName, categoryName, now)
        }
    }

    private suspend fun upsertMerchant(name: String): Merchant? {
        val normalized = normalizeMerchant(name)
        if (normalized.isBlank()) return null
        merchantDao.getByNormalizedName(normalized)?.let { return it }
        val now = System.currentTimeMillis()
        merchantDao.insert(Merchant(name = name, normalizedName = normalized, createdAt = now, updatedAt = now))
        return merchantDao.getByNormalizedName(normalized)
    }

    suspend fun cleanupDuplicates(dedupWindowMillis: Long = 10 * 60 * 1000L): Int {
        val all = expenseDao.getAllOnce()
        val lastSeenTimestamp = HashMap<Pair<Long, String>, Long>()
        val toDelete = mutableListOf<Expense>()

        for (expense in all) {
            val key = expense.amountMinor to expense.bankName
            val previousTimestamp = lastSeenTimestamp[key]
            if (previousTimestamp != null && expense.timestamp - previousTimestamp <= dedupWindowMillis) {
                toDelete += expense
            }
            lastSeenTimestamp[key] = expense.timestamp
        }

        toDelete.forEach { expenseDao.delete(it) }
        return toDelete.size
    }

    // ===== الفئات والقواعد =====

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()
    suspend fun addCategory(name: String): Long = categoryDao.insert(Category(name = name, isBuiltIn = false))
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    fun observeRules(): Flow<List<SmsRule>> = smsRuleDao.observeAll()
    suspend fun insertRule(rule: SmsRule): Long = smsRuleDao.insert(rule)
    suspend fun deleteRule(rule: SmsRule) = smsRuleDao.delete(rule)

    fun observeMerchants(): Flow<List<Merchant>> = merchantDao.observeAll()
    fun observeMerchantRules(): Flow<List<MerchantRule>> = merchantRuleDao.observeAll()
    suspend fun saveMerchantRule(rule: MerchantRule) {
        if (rule.id == 0L) {
            merchantRuleDao.insert(rule.copy(createdAt = System.currentTimeMillis()))
        } else {
            merchantRuleDao.update(rule)
        }
    }
    suspend fun deleteMerchantRule(rule: MerchantRule) = merchantRuleDao.delete(rule)

    suspend fun merchantStats(merchant: String, range: TimeRange): MerchantStats? =
        expenseDao.getMerchantStats(merchant, range.start, range.end)

    /** تحليلات جهة واحدة (المرحلة 4، بند 16): الشهر ده، الشهر اللي فات، وتاريخ 6 شهور. */
    suspend fun merchantAnalytics(merchant: String, months: Int = 6): MerchantAnalytics {
        val thisMonth = merchantStats(merchant, monthRange())
        val lastMonth = merchantStats(merchant, monthRangeOffset(-1))
        val history = (0 downTo -(months - 1)).map { offset ->
            val range = monthRangeOffset(offset)
            monthLabel(range.start) to (merchantStats(merchant, range)?.total ?: 0L)
        }
        val count = thisMonth?.count ?: 0
        return MerchantAnalytics(
            merchant = merchant,
            thisMonthMinor = thisMonth?.total ?: 0L,
            lastMonthMinor = lastMonth?.total ?: 0L,
            transactionCount = count,
            averageMinor = if (count > 0) (thisMonth?.total ?: 0L) / count else 0L,
            highestMinor = thisMonth?.maxMinor ?: 0L,
            history = history
        )
    }

    // ===== الحسابات =====

    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll()
    suspend fun saveAccount(account: Account) {
        val now = System.currentTimeMillis()
        if (account.id == 0L) {
            accountDao.insert(account.copy(createdAt = now, updatedAt = now))
        } else {
            accountDao.update(account.copy(updatedAt = now))
        }
    }
    suspend fun deleteAccount(account: Account) = accountDao.delete(account)
    suspend fun accountBalance(accountId: Long): Long = accountDao.getBalance(accountId)

    /**
     * تحويل بين حسابين (المرحلة 5). بيتسجّل كحركة واحدة نوعها TRANSFER فيها
     * الحسابين، فمينفعش تتحسب كمصروف في أي استعلام (كل تجميعات المصروفات
     * بتفلتر type IN ('EXPENSE','REFUND')).
     */
    suspend fun addTransfer(
        amountMinor: Long,
        fromAccountId: Long?,
        toAccountId: Long?,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Long = insertTransaction(
        Expense(
            amountMinor = amountMinor,
            type = TransactionType.TRANSFER,
            merchant = "تحويل بين الحسابات",
            bankName = "تحويل",
            timestamp = timestamp,
            rawBody = "",
            categoryName = "تحويلات",
            accountId = fromAccountId,
            toAccountId = toAccountId,
            source = TransactionSource.MANUAL,
            note = note
        )
    )

    // ===== الميزانيات =====

    fun observeBudgets(): Flow<List<Budget>> = budgetDao.observeCategoryBudgets()
    fun observeOverallBudget(): Flow<Long?> = budgetDao.observeOverallLimit()
    suspend fun setBudget(budget: Budget) = budgetDao.insert(budget)
    suspend fun setOverallBudget(limitMinor: Long) =
        if (limitMinor > 0L) budgetDao.insert(Budget(Budget.OVERALL_KEY, limitMinor))
        else budgetDao.delete(Budget.OVERALL_KEY)
    suspend fun deleteBudget(categoryName: String) = budgetDao.delete(categoryName)

    // ===== الدوريات والاشتراكات =====

    fun observeRecurringExpenses(): Flow<List<RecurringExpense>> = recurringExpenseDao.observeRecurringOnly()
    fun observeSubscriptions(): Flow<List<RecurringExpense>> = recurringExpenseDao.observeSubscriptions()
    fun observeAllRecurring(): Flow<List<RecurringExpense>> = recurringExpenseDao.observeAll()
    suspend fun getRecurringExpensesSync(): List<RecurringExpense> = recurringExpenseDao.getAllSync()
    suspend fun insertRecurringExpense(expense: RecurringExpense): Long = recurringExpenseDao.insert(expense)
    suspend fun updateRecurringExpense(expense: RecurringExpense) = recurringExpenseDao.update(expense)
    suspend fun deleteRecurringExpense(expense: RecurringExpense) = recurringExpenseDao.delete(expense)
    suspend fun setRecurringActive(expense: RecurringExpense, active: Boolean) =
        recurringExpenseDao.update(expense.copy(isActive = active))

    /**
     * تسجيل الدفعات الدورية المستحقة. بيرجّع الحركات اللي اتسجلت فعلاً (عشان
     * اللي بينادي يقدر يفحص تنبيهات الميزانية لكل واحدة).
     *
     * الشهري بيستخدم [RecurringExpense.lastAddedMonth] زي الأول (مرة واحدة في
     * الشهر بحد أقصى)، والباقي بيستخدم [RecurringExpense.nextDueDate].
     */
    suspend fun processDueRecurring(now: Long = System.currentTimeMillis()): List<Expense> {
        val inserted = mutableListOf<Expense>()
        val currentMonth = monthKey(now)
        val today = dayOfMonth(now)

        for (item in recurringExpenseDao.getActive()) {
            val isDue = if (item.frequency == Frequency.MONTHLY) {
                today >= item.dayOfMonth && item.lastAddedMonth != currentMonth
            } else {
                item.nextDueDate in 1..now
            }
            if (!isDue) continue

            val expense = Expense(
                amountMinor = item.amountMinor,
                merchant = item.displayName,
                bankName = item.bankName,
                timestamp = now,
                rawBody = "Recurring: ${item.displayName}",
                categoryName = item.categoryName,
                accountId = item.accountId,
                source = TransactionSource.RECURRING
            )
            insertTransaction(expense)
            inserted += expense

            recurringExpenseDao.update(
                item.copy(
                    lastAddedMonth = currentMonth,
                    nextDueDate = nextDueDate(
                        from = if (item.nextDueDate > 0) item.nextDueDate else now,
                        frequency = item.frequency,
                        intervalDays = item.intervalDays
                    )
                )
            )
        }
        return inserted
    }

    // ===== الأقساط =====

    fun observeInstallments(): Flow<List<Installment>> = installmentDao.observeAll()
    suspend fun saveInstallment(installment: Installment) {
        if (installment.id == 0L) {
            installmentDao.insert(installment.copy(createdAt = System.currentTimeMillis()))
        } else {
            installmentDao.update(installment)
        }
    }
    suspend fun deleteInstallment(installment: Installment) = installmentDao.delete(installment)

    /**
     * تسجيل قسط مدفوع. **إجمالي المشترى عمره ما بيتسجّل كحركة** — القسط بس،
     * وموسوم بـ installmentId، فتحليلات الشهر مبتعدّش نفس الفلوس مرتين.
     */
    suspend fun payInstallment(installment: Installment, now: Long = System.currentTimeMillis()): Boolean {
        if (installment.remainingCount <= 0) return false

        insertTransaction(
            Expense(
                amountMinor = installment.installmentMinor,
                merchant = installment.merchant.ifBlank { installment.title },
                bankName = "قسط",
                timestamp = now,
                rawBody = "Installment: ${installment.title}",
                categoryName = installment.categoryName,
                accountId = installment.accountId,
                source = TransactionSource.MANUAL,
                installmentId = installment.id,
                note = "قسط ${installment.paidCount + 1} من ${installment.count}"
            )
        )

        val paid = installment.paidCount + 1
        installmentDao.update(
            installment.copy(
                paidCount = paid,
                isActive = paid < installment.count,
                nextDueDate = nextDueDate(installment.nextDueDate.takeIf { it > 0 } ?: now, Frequency.MONTHLY)
            )
        )
        return true
    }

    // ===== الدفعات القادمة =====

    suspend fun upcomingPayments(withinDays: Int = 45, now: Long = System.currentTimeMillis()): List<UpcomingPayment> {
        val horizon = now + withinDays * 24L * 60 * 60 * 1000
        val fromRecurring = recurringExpenseDao.getActive().mapNotNull { item ->
            val due = if (item.nextDueDate > 0) {
                item.nextDueDate
            } else {
                firstDueDateForDayOfMonth(now, item.dayOfMonth)
            }
            if (due > horizon) return@mapNotNull null
            UpcomingPayment(
                name = item.displayName,
                amountMinor = item.amountMinor,
                dueDate = due,
                kind = if (item.isSubscription) UpcomingKind.SUBSCRIPTION else UpcomingKind.RECURRING
            )
        }
        val fromInstallments = installmentDao.getActive()
            .filter { it.nextDueDate in 1..horizon }
            .map {
                UpcomingPayment(it.title, it.installmentMinor, it.nextDueDate, UpcomingKind.INSTALLMENT)
            }
        return (fromRecurring + fromInstallments).sortedBy { it.dueDate }
    }

    /**
     * مهلة التنبيه المحددة للدفعة دي (بالاسم). الأقساط مفيهاش مهلة خاصة،
     * فبتاخد يوم واحد زي الافتراضي.
     */
    suspend fun reminderDaysBefore(name: String, default: Int = 1): Int =
        recurringExpenseDao.getActive()
            .firstOrNull { it.displayName == name }
            ?.reminderDaysBefore
            ?: default

    // ===== محرّك التحليلات =====

    suspend fun monthSummary(range: TimeRange): MonthSummary = MonthSummary(
        incomeMinor = expenseDao.getSumByType(TransactionType.INCOME, range.start, range.end) ?: 0L,
        expenseMinor = expenseDao.getSumByType(TransactionType.EXPENSE, range.start, range.end) ?: 0L,
        refundMinor = expenseDao.getSumByType(TransactionType.REFUND, range.start, range.end) ?: 0L,
        transferMinor = expenseDao.getSumByType(TransactionType.TRANSFER, range.start, range.end) ?: 0L
    )

    /**
     * كل أرقام الشهر في كائن واحد — الداشبورد والرؤى والتقارير والمساعد كلهم
     * بيقروا منه، فمفيش فرصة إن شاشة تعرض رقم مختلف عن شاشة تانية.
     *
     * [monthsFromNow] = 0 الشهر الحالي، -1 الشهر اللي فات... إلخ.
     */
    suspend fun buildFinancialContext(monthsFromNow: Int = 0): FinancialContext {
        val now = System.currentTimeMillis()
        val range = if (monthsFromNow == 0) monthRange(now) else monthRangeOffset(monthsFromNow, now)
        val previousRange = monthRangeOffset(monthsFromNow - 1, now)

        val summary = monthSummary(range)
        val previousSummary = monthSummary(previousRange)

        val categoryTotals = expenseDao.getTotalsByCategoryBetween(range.start, range.end)
            .associate { it.categoryName to it.total }
        val previousCategoryTotals = expenseDao.getTotalsByCategoryBetween(previousRange.start, previousRange.end)
            .associate { it.categoryName to it.total }

        val budgets = budgetDao.getAllOnce()
        val overall = budgets.firstOrNull { it.categoryName == Budget.OVERALL_KEY }?.limitMinor ?: 0L
        val categoryBudgets = budgets.filter { it.categoryName != Budget.OVERALL_KEY }
            .associate { it.categoryName to it.limitMinor }

        // الشهر الحالي: الأيام المنقضية لحد النهارده. أي شهر تاني: الشهر كامل.
        val daysInThisMonth = daysInMonth(range.start)
        val elapsed = if (monthsFromNow == 0) dayOfMonth(now) else daysInThisMonth
        val previousDays = daysInMonth(previousRange.start)

        val subscriptions = recurringExpenseDao.getActive()
            .filter { it.isSubscription }
            .sumOf { monthlyEquivalentMinor(it.amountMinor, it.frequency, it.intervalDays) }
        val installmentsLoad = installmentDao.getActive().sumOf { it.installmentMinor }

        return FinancialContext(
            monthLabel = monthLabel(range.start),
            summary = summary,
            categoryTotals = categoryTotals,
            previousCategoryTotals = previousCategoryTotals,
            topMerchants = expenseDao.getTopMerchantsBetween(range.start, range.end, 5)
                .map { it.merchant to it.total },
            forecast = forecast(
                netSpentMinor = summary.netSpentMinor,
                daysElapsed = elapsed,
                daysInMonth = daysInThisMonth,
                budgetLimitMinor = overall
            ),
            overallBudgetMinor = overall,
            categoryBudgets = categoryBudgets,
            previousNetSpentMinor = previousSummary.netSpentMinor,
            previousDailyAverageMinor = dailyAverageMinor(previousSummary.netSpentMinor, previousDays),
            subscriptionsMonthlyMinor = subscriptions,
            installmentsMonthlyMinor = installmentsLoad,
            upcoming = upcomingPayments(now = now)
        )
    }

    /** متوسط وعدد عمليات الفئة — لكشف الحركات الشاذة. */
    suspend fun categoryAverageAndCount(categoryName: String, sinceTime: Long): Pair<Double?, Int> =
        expenseDao.getCategoryAverage(categoryName, sinceTime) to
            expenseDao.getCategoryCount(categoryName, sinceTime)

    // ===== النسخ الاحتياطي (بيستخدمها BackupManager) =====

    internal suspend fun snapshot(): BackupSnapshot = BackupSnapshot(
        expenses = expenseDao.getAllOnce(),
        categories = categoryDao.getAllOnce(),
        smsRules = smsRuleDao.getAllOnce(),
        budgets = budgetDao.getAllOnce(),
        recurring = recurringExpenseDao.getAllSync(),
        accounts = accountDao.getAllOnce(),
        merchants = merchantDao.getAllOnce(),
        merchantRules = merchantRuleDao.getAllOnce(),
        installments = installmentDao.getAllOnce()
    )

    /**
     * استرجاع نسخة: بيمسح كل الجداول وبيكتب المحتوى المستورد. الاستدعاء لازم
     * يكون جوه runInTransaction (راجع BackupManager) عشان لو حصل خطأ في النص،
     * البيانات القديمة ما تضيعش.
     */
    internal suspend fun replaceAll(snapshot: BackupSnapshot) {
        expenseDao.deleteAll()
        categoryDao.deleteAll()
        smsRuleDao.deleteAll()
        budgetDao.deleteAll()
        recurringExpenseDao.deleteAll()
        accountDao.deleteAll()
        merchantDao.deleteAll()
        merchantRuleDao.deleteAll()
        installmentDao.deleteAll()

        expenseDao.insertAll(snapshot.expenses)
        categoryDao.insertAll(snapshot.categories)
        smsRuleDao.insertAll(snapshot.smsRules)
        budgetDao.insertAll(snapshot.budgets)
        recurringExpenseDao.insertAll(snapshot.recurring)
        accountDao.insertAll(snapshot.accounts)
        merchantDao.insertAll(snapshot.merchants)
        merchantRuleDao.insertAll(snapshot.merchantRules)
        installmentDao.insertAll(snapshot.installments)
    }
}

/** محتوى نسخة احتياطية كاملة (كل الجداول). */
data class BackupSnapshot(
    val expenses: List<Expense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val smsRules: List<SmsRule> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurring: List<RecurringExpense> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val merchants: List<Merchant> = emptyList(),
    val merchantRules: List<MerchantRule> = emptyList(),
    val installments: List<Installment> = emptyList()
) {
    val totalRows: Int
        get() = expenses.size + categories.size + smsRules.size + budgets.size + recurring.size +
            accounts.size + merchants.size + merchantRules.size + installments.size
}
