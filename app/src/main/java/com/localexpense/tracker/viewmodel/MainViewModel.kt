package com.localexpense.tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.data.AppDatabase
import com.localexpense.tracker.data.CategoryTotal
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.data.SourceTotal
import com.localexpense.tracker.parser.SmsImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ExpenseRepository(database.expenseDao())

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _totalsBySource = MutableStateFlow<List<SourceTotal>>(emptyList())
    val totalsBySource: StateFlow<List<SourceTotal>> = _totalsBySource.asStateFlow()

    private val _totalsByCategory = MutableStateFlow<List<CategoryTotal>>(emptyList())
    val totalsByCategory: StateFlow<List<CategoryTotal>> = _totalsByCategory.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    init {
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

    fun scanInbox() {
        viewModelScope.launch {
            val (scanned, inserted) = SmsImporter.importAllSms(getApplication())
            _importStatusMessage.value = "تم فحص $scanned رسالة، وتسجيل $inserted مصروف جديد"
        }
    }
}