package com.localexpense.tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.data.Category
import com.localexpense.tracker.data.CategoryTotal
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.data.SmsRule
import com.localexpense.tracker.data.SourceTotal
import com.localexpense.tracker.parser.SmsImporter
import com.localexpense.tracker.parser.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class RuleTestResult(
    val matched: Boolean,
    val amount: Double? = null,
    val merchant: String? = null,
    val bankName: String? = null
)

sealed class ImportState {
    data object Idle : ImportState()
    data object Running : ImportState()
    data class Done(val scanned: Int, val imported: Int) : ImportState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(application)

    val expenses: StateFlow<List<Expense>> = repository.observeExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<SmsRule>> = repository.observeRules()
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

    val monthTotal: StateFlow<Double> = monthRange.let { (s, e) ->
        repository.observeTotalBetween(s, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthTotalsBySource: StateFlow<List<SourceTotal>> = monthRange.let { (s, e) ->
        repository.observeTotalsBySource()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthTotalsByCategory: StateFlow<List<CategoryTotal>> = monthRange.let { (s, e) ->
        repository.observeTotalsByCategory()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repository.addCategory(trimmed) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }

    fun addManualExpense(amount: Double, merchant: String, category: String) {
        viewModelScope.launch {
            repository.addExpense(
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

    // Root Fix: Method called by AppNavHost
    fun saveExpense(amount: Double, date: Long, category: String) {
        viewModelScope.launch {
            repository.addExpense(
                Expense(
                    amount = amount,
                    merchant = "يدوي",
                    bankName = "يدوي",
                    timestamp = if (date > 0) date else System.currentTimeMillis(),
                    rawBody = "",
                    categoryName = category
                )
            )
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { repository.updateExpense(expense) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    fun saveRule(rule: SmsRule) {
        viewModelScope.launch { repository.saveRule(rule) }
    }

    fun deleteRule(rule: SmsRule) {
        viewModelScope.launch { repository.deleteRule(rule) }
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    fun importFromInbox() {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            val currentRules = rules.first().filter { it.isEnabled }
            val existingRaw = expenses.first().map { it.rawBody }.toSet()

            val (scanned, imported, found) = withContext(Dispatchers.IO) {
                SmsImporter.importAllSmsWithRules(getApplication(), currentRules, existingRaw)
            }

            found.forEach { expense ->
                repository.addExpense(expense)
            }
            _importState.value = ImportState.Done(scanned = scanned, imported = imported)
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    fun testRule(sender: String, body: String, rule: SmsRule): RuleTestResult {
        val expense = SmsParser.parseSms(sender, body, System.currentTimeMillis())
        return if (expense != null) {
            RuleTestResult(
                matched = true,
                amount = expense.amount,
                merchant = expense.merchant,
                bankName = expense.bankName
            )
        } else {
            RuleTestResult(matched = false)
        }
    }
}
