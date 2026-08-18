package com.localexpense.tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.Category
import com.localexpense.tracker.data.CategoryTotal
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.data.SmsRule
import com.localexpense.tracker.data.SourceTotal
import com.localexpense.tracker.parser.SmsImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface ImportState {
    data object Idle : ImportState
    data object Running : ImportState
    data class Done(val scanned: Int, val imported: Int) : ImportState
    data class Error(val message: String) : ImportState
}

data class RuleTestResult(
    val matched: Boolean,
    val amount: Double? = null,
    val merchant: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ExpenseRepository(database.expenseDao())

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _totalsBySource = MutableStateFlow<List<SourceTotal>>(emptyList())
    val totalsBySource: StateFlow<List<SourceTotal>> = _totalsBySource.asStateFlow()

    private val _totalsByCategory = MutableStateFlow<List<CategoryTotal>>(emptyList())
    val totalsByCategory: StateFlow<List<CategoryTotal>> = _totalsByCategory.asStateFlow()

    private val _monthTotalsByCategory = MutableStateFlow<List<CategoryTotal>>(emptyList())
    val monthTotalsByCategory: StateFlow<List<CategoryTotal>> = _monthTotalsByCategory.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _rules = MutableStateFlow<List<SmsRule>>(emptyList())
    val rules: StateFlow<List<SmsRule>> = _rules.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { _expenses.value = it }
        }
        viewModelScope.launch {
            repository.observeTotalsBySource().collect { _totalsBySource.value = it }
        }
        viewModelScope.launch {
            repository.observeTotalsByCategory().collect { _totalsByCategory.value = it }
        }
        
        loadCurrentMonthCategoryTotals()
    }

    private fun loadCurrentMonthCategoryTotals() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val endTime = calendar.timeInMillis - 1

        viewModelScope.launch {
            database.expenseDao().observeTotalsByCategoryBetween(startTime, endTime)
                .collect { _monthTotalsByCategory.value = it }
        }
    }

    fun addExpense(
        amount: Double,
        merchant: String,
        bankName: String,
        timestamp: Long = System.currentTimeMillis(),
        rawBody: String = "",
        categoryName: String = "عام"
    ) {
        viewModelScope.launch {
            val expense = Expense(
                amount = amount,
                merchant = merchant,
                bankName = bankName,
                timestamp = timestamp,
                rawBody = rawBody,
                categoryName = categoryName
            )
            repository.insert(expense)
        }
    }

    fun saveExpense(
        amount: Double,
        timestamp: Long = System.currentTimeMillis(),
        categoryName: String = "عام",
        merchant: String = "يدوي",
        bankName: String = "يدوي"
    ) {
        addExpense(
            amount = amount,
            merchant = merchant,
            bankName = bankName,
            timestamp = timestamp,
            categoryName = categoryName
        )
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { repository.update(expense) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repository.delete(expense) }
    }

    fun importFromInbox() {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            try {
                val (scanned, inserted) = SmsImporter.importAllSms(getApplication())
                _importState.value = ImportState.Done(scanned, inserted)
                _importStatusMessage.value = "تم فحص $scanned رسالة، وتسجيل $inserted مصروف جديد"
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.localizedMessage ?: "حدث خطأ أثناء الاستيراد")
            }
        }
    }

    fun scanInbox() = importFromInbox()

    fun saveRule(rule: SmsRule) {
        viewModelScope.launch {
            val currentList = _rules.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == rule.id }
            if (index != -1) {
                currentList[index] = rule
            } else {
                currentList.add(rule)
            }
            _rules.value = currentList
        }
    }

    fun deleteRule(rule: SmsRule) {
        viewModelScope.launch {
            _rules.value = _rules.value.filter { it.id != rule.id }
        }
    }

    fun testRule(sender: String, body: String, rule: SmsRule): RuleTestResult {
        val senderRegex = rule.senderPattern.takeIf { it.isNotBlank() }?.let { Regex(it, RegexOption.IGNORE_CASE) }
        val keywordRegex = rule.debitKeywordPattern.takeIf { it.isNotBlank() }?.let { Regex(it, RegexOption.IGNORE_CASE) }

        val senderMatched = senderRegex?.containsMatchIn(sender) ?: true
        val keywordMatched = keywordRegex?.containsMatchIn(body) ?: true

        if (!senderMatched || !keywordMatched) {
            return RuleTestResult(matched = false)
        }

        val amount = Regex(rule.amountPattern, RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()

        val merchantMatch = rule.merchantPattern.takeIf { it.isNotBlank() }?.let {
            Regex(it, RegexOption.IGNORE_CASE).find(body)?.groupValues?.getOrNull(1)
        }

        return RuleTestResult(
            matched = amount != null,
            amount = amount,
            merchant = merchantMatch
        )
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val newCategory = Category(name = name, isBuiltIn = false)
            _categories.value = _categories.value + newCategory
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            _categories.value = _categories.value.filter { it.id != category.id }
        }
    }
}