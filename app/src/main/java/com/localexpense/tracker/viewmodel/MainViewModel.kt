package com.localexpense.tracker.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.data.Category
import com.localexpense.tracker.data.CategoryTotal
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.data.SourceTotal
import com.localexpense.tracker.parser.SmsImporter
import com.localexpense.tracker.parser.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

import com.localexpense.tracker.data.SmsRule

sealed class CleanupState {
    data object Idle : CleanupState()
    data object Running : CleanupState()
    data class Done(val removed: Int) : CleanupState()
}

data class RuleTestResult(
    val matched: Boolean,
    val amount: Double? = null,
    val merchant: String? = null,
    val bankName: String? = null
)

data class SmsTestResult(
    val matched: Boolean,
    val amount: Double? = null,
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(application)

    val expenses: StateFlow<List<Expense>> = repository.observeExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val monthRange: Pair<Long, Long> get() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis - 1
        return start to end
    }

    val monthTotalsBySource: StateFlow<List<SourceTotal>> = repository.observeTotalsBySource()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthTotalsByCategory: StateFlow<List<CategoryTotal>> = monthRange.let { (s, e) ->
        repository.observeTotalsByCategoryBetween(s, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthTotal: StateFlow<Double> = monthTotalsByCategory
        .map { list -> list.sumOf { it.total } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _cleanupState = MutableStateFlow<CleanupState>(CleanupState.Idle)
    val cleanupState: StateFlow<CleanupState> = _cleanupState.asStateFlow()

    val rules: StateFlow<List<SmsRule>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prefs = application.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)
    private val _archivedYears = MutableStateFlow<Set<String>>(
        prefs.getStringSet("archived_years", emptySet()) ?: emptySet()
    )
    val archivedYears: StateFlow<Set<String>> = _archivedYears.asStateFlow()

    fun archiveYears(years: Set<String>) {
        val current = _archivedYears.value.toMutableSet()
        current.addAll(years)
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

    fun addManualExpense(amount: Double, merchant: String, category: String = "عام") {
        viewModelScope.launch {
            repository.insertExpense(
                Expense(
                    amount = amount,
                    merchant = merchant,
                    bankName = "يدوي",
                    timestamp = System.currentTimeMillis(),
                    rawBody = "",
                    categoryName = category
                )
            )
        }
    }

    fun saveExpense(amount: Double, merchant: String, category: String) {
        addManualExpense(amount, merchant, category)
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { repository.updateExpense(expense) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    fun saveRule(rule: SmsRule) {
        viewModelScope.launch { repository.insertRule(rule) }
    }

    fun deleteRule(rule: SmsRule) {
        viewModelScope.launch { repository.deleteRule(rule) }
    }

    fun cleanupDuplicateExpenses() {
        viewModelScope.launch {
            _cleanupState.value = CleanupState.Running
            // Dummy logic or real if repository has it
            val removed = withContext(Dispatchers.IO) { repository.cleanupDuplicates() }
            _cleanupState.value = CleanupState.Done(removed)
        }
    }

    fun importFromInbox() {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            val (scanned, imported) = withContext(Dispatchers.IO) {
                SmsImporter.importAllSms(getApplication())
            }
            _importState.value = ImportState.Done(scanned = scanned, imported = imported)
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    fun testSmsMessage(sender: String, body: String): SmsTestResult {
        val result = SmsParser.parseSms(sender, body, System.currentTimeMillis())
            ?: return SmsTestResult(matched = false)
        return SmsTestResult(
            matched = true,
            amount = result.amount,
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
            val amountStr = amountMatch?.groupValues?.getOrNull(1)?.replace(",", "")
            val amount = amountStr?.toDoubleOrNull() ?: return RuleTestResult(matched = false)

            var merchant: String? = null
            if (rule.merchantPattern.isNotBlank()) {
                val merchantRegex = Regex(rule.merchantPattern, RegexOption.IGNORE_CASE)
                val merchantMatch = merchantRegex.find(body)
                merchant = merchantMatch?.groupValues?.getOrNull(1)?.trim()
            }

            return RuleTestResult(
                matched = true,
                amount = amount,
                merchant = merchant,
                bankName = rule.bankName
            )
        } catch (e: Exception) {
            return RuleTestResult(matched = false)
        }
    }
}