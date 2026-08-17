package com.localexpense.tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.data.SmsRule
import com.localexpense.tracker.data.SourceTotal
import com.localexpense.tracker.parser.SmsParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class RuleTestResult(
    val matched: Boolean,
    val amount: Double? = null,
    val merchant: String? = null,
    val bankName: String? = null
)

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
        repository.observeTotalsBySource(s, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addManualExpense(amount: Double, merchant: String, category: String) {
        viewModelScope.launch {
            repository.addExpense(
                Expense(
                    amount = amount,
                    merchant = merchant,
                    source = "يدوي",
                    timestampMillis = System.currentTimeMillis(),
                    rawMessage = "",
                    category = category,
                    isConfirmed = true
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

    /** Used by the "test rule" screen: paste a real message, see what would be extracted. */
    fun testRule(sender: String, body: String, rule: SmsRule): RuleTestResult {
        val result = SmsParser.parse(sender, body, listOf(rule))
            ?: return RuleTestResult(matched = false)
        return RuleTestResult(
            matched = true,
            amount = result.amount,
            merchant = result.merchant,
            bankName = result.source
        )
    }
}
