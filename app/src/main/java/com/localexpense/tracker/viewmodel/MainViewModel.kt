package com.localexpense.tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.Category
import com.localexpense.tracker.data.CategoryTotal
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.data.SourceTotal
import com.localexpense.tracker.parser.SmsImporter
import com.localexpense.tracker.parser.SmsParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface ImportState {
    data object Idle : ImportState
    data object Running : ImportState
    data class Done(val scanned: Int, val imported: Int) : ImportState
    data class Error(val message: String) : ImportState
}

sealed interface CleanupState {
    data object Idle : CleanupState
    data object Running : CleanupState
    data class Done(val removed: Int) : CleanupState
}

/** Result of running the parser against a pasted real message, for the "اختبار رسالة" screen. */
data class SmsTestResult(
    val matched: Boolean,
    val amount: Double? = null,
    val merchant: String? = null,
    val bankName: String? = null,
    val categoryName: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ExpenseRepository(database.expenseDao(), database.categoryDao())

    val expenses: StateFlow<List<Expense>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalsBySource: StateFlow<List<SourceTotal>> = repository.observeTotalsBySource()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.observeCategories()
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

    val monthTotalsByCategory: StateFlow<List<CategoryTotal>> = monthRange.let { (s, e) ->
        repository.observeTotalsByCategoryBetween(s, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _cleanupState = MutableStateFlow<CleanupState>(CleanupState.Idle)
    val cleanupState: StateFlow<CleanupState> = _cleanupState.asStateFlow()

    /** Manual entry from the "إضافة مصروف يدوي" screen. */
    fun addManualExpense(amount: Double, merchant: String, categoryName: String) {
        viewModelScope.launch {
            repository.insert(
                Expense(
                    amount = amount,
                    merchant = merchant.ifBlank { "مصروف يدوي" },
                    bankName = "يدوي",
                    timestamp = System.currentTimeMillis(),
                    rawBody = "",
                    categoryName = categoryName.ifBlank { "عام" }
                )
            )
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { repository.update(expense) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repository.delete(expense) }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repository.addCategory(trimmed) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }

    /**
     * Scans the phone's existing SMS inbox and backfills matching expenses.
     * Safe to run more than once — messages already imported are skipped (see ExpenseDao.exists).
     */
    fun importFromInbox() {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            try {
                val (scanned, inserted) = SmsImporter.importAllSms(getApplication())
                _importState.value = ImportState.Done(scanned, inserted)
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.localizedMessage ?: "حدث خطأ أثناء الاستيراد")
            }
        }
    }

    /**
     * بيمسح المصروفات المكررة اللي اتسجلت قبل إصلاح منطق منع التكرار
     * (راجع ExpenseRepository.cleanupDuplicates للتفاصيل).
     */
    fun cleanupDuplicateExpenses() {
        viewModelScope.launch {
            _cleanupState.value = CleanupState.Running
            val removed = repository.cleanupDuplicates()
            _cleanupState.value = CleanupState.Done(removed)
        }
    }

    /** Used by the "اختبار رسالة" screen: paste a real message, see what would be extracted. */
    fun testSmsMessage(sender: String, body: String): SmsTestResult {
        val expense = SmsParser.parseSms(sender, body, System.currentTimeMillis())
            ?: return SmsTestResult(matched = false)
        return SmsTestResult(
            matched = true,
            amount = expense.amount,
            merchant = expense.merchant,
            bankName = expense.bankName,
            categoryName = expense.categoryName
        )
    }
}
