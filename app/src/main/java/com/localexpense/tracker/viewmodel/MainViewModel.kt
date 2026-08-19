package com.localexpense.tracker.viewmodel

import android.app.Application
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

data class SmsRule(
    val id: Long = 0,
    val isEnabled: Boolean = true
)

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

    fun saveRule(rule: SmsRule) {}

    fun deleteRule(rule: SmsRule) {}

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

    fun testRule(sender: String, body: String, rule: SmsRule): RuleTestResult {
        val result = SmsParser.parseSms(sender, body, System.currentTimeMillis())
            ?: return RuleTestResult(matched = false)
        return RuleTestResult(
            matched = true,
            amount = result.amount,
            merchant = result.merchant,
            bankName = result.bankName
        )
    }
}