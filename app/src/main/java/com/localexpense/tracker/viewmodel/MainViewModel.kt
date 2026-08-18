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

// حالات عملية الاستيراد (ImportState)
sealed interface ImportState {
    object Idle : ImportState
    object Running : ImportState
    data class Done(val scanned: Int, val imported: Int) : ImportState
    data class Error(val message: String) : ImportState
}

// نتيجة اختبار القاعدة (RuleTestResult)
data class RuleTestResult(
    val matched: Boolean,
    val amount: Double? = null,
    val merchant: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ExpenseRepository(database.expenseDao())

    // 1. المصروفات
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    // 2. الإجماليات
    private val _totalsBySource = MutableStateFlow<List<SourceTotal>>(emptyList())
    val totalsBySource: StateFlow<List<SourceTotal>> = _totalsBySource.asStateFlow()

    private val _totalsByCategory = MutableStateFlow<List<CategoryTotal>>(emptyList())
    val totalsByCategory: StateFlow<List<CategoryTotal>> = _totalsByCategory.asStateFlow()

    val monthTotalsByCategory: StateFlow<List<CategoryTotal>> = _totalsByCategory.asStateFlow()

    // 3. الفئات
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    // 4. القواعد
    private val _rules = MutableStateFlow<List<SmsRule>>(emptyList())
    val rules: StateFlow<List<SmsRule>> = _rules.asStateFlow()

    // 5. حالة الاستيراد
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    init {
        // مراقبة المصروفات والإجماليات
        viewModelScope.launch {
            repository.observeAll().collect { list ->
                _expenses.value = list
            }
        }
        viewModelScope.launch {
            repository.observeTotalsBySource().collect { list ->
                _totalsBySource.value = list
            }
        }
        viewModelScope.launch {
            repository.observeTotalsByCategory().collect { list ->
                _totalsByCategory.value = list
            }
        }
    }

    // --- إدارة المصروفات (Expenses) ---

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
        viewModelScope.launch {
            repository.update(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.delete(expense)
        }
    }

    // --- استيراد الرسائل (SMS Import) ---

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

    fun scanInbox() {
        importFromInbox()
    }

    // --- إدارة القواعد (Rules) ---

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
        val senderMatched = rule.senderPattern.isBlank() || 
            Regex(rule.senderPattern, RegexOption.IGNORE_CASE).containsMatchIn(sender)
        val keywordMatched = rule.debitKeywordPattern.isBlank() || 
            Regex(rule.debitKeywordPattern, RegexOption.IGNORE_CASE).containsMatchIn(body)

        if (!senderMatched || !keywordMatched) {
            return RuleTestResult(matched = false)
        }

        val amountMatch = Regex(rule.amountPattern, RegexOption.IGNORE_CASE).find(body)
        val amount = amountMatch?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()

        val merchantMatch = if (rule.merchantPattern.isNotBlank()) {
            Regex(rule.merchantPattern, RegexOption.IGNORE_CASE).find(body)?.groupValues?.getOrNull(1)
        } else null

        return RuleTestResult(
            matched = amount != null,
            amount = amount,
            merchant = merchantMatch
        )
    }

    // --- إدارة الفئات (Categories) ---

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