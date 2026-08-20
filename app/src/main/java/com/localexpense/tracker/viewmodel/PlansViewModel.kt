package com.localexpense.tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.data.Expense
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.data.Frequency
import com.localexpense.tracker.data.Installment
import com.localexpense.tracker.data.RecurringExpense
import com.localexpense.tracker.domain.UpcomingPayment
import com.localexpense.tracker.domain.firstDueDateForDayOfMonth
import com.localexpense.tracker.util.dayRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * الدفعات الدورية والاشتراكات والأقساط والتقويم (المراحل 12-15).
 * الاشتراك = دفعة دورية بـ isSubscription = true، فالشاشتين بيقروا من نفس
 * الجدول بفلتر مختلف.
 */
class PlansViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(application)

    val recurring: StateFlow<List<RecurringExpense>> = repository.observeRecurringExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<RecurringExpense>> = repository.observeSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installments: StateFlow<List<Installment>> = repository.observeInstallments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _upcoming = MutableStateFlow<List<UpcomingPayment>>(emptyList())
    val upcoming: StateFlow<List<UpcomingPayment>> = _upcoming.asStateFlow()

    /** حركات اليوم المختار في التقويم (المرحلة 15). */
    private val _selectedDay = MutableStateFlow(System.currentTimeMillis())
    val selectedDay: StateFlow<Long> = _selectedDay.asStateFlow()

    private val _dayTransactions = MutableStateFlow<List<Expense>>(emptyList())
    val dayTransactions: StateFlow<List<Expense>> = _dayTransactions.asStateFlow()

    init {
        refreshUpcoming()
        selectDay(System.currentTimeMillis())
    }

    fun refreshUpcoming() {
        viewModelScope.launch {
            _upcoming.value = withContext(Dispatchers.IO) { repository.upcomingPayments() }
        }
    }

    fun selectDay(timestamp: Long) {
        _selectedDay.value = timestamp
        viewModelScope.launch {
            val range = dayRange(timestamp)
            _dayTransactions.value = withContext(Dispatchers.IO) {
                repository.searchOnce(
                    com.localexpense.tracker.data.TransactionFilter(
                        startTime = range.start,
                        endTime = range.end,
                        limit = 500
                    )
                )
            }
        }
    }

    // ===== دوريات واشتراكات =====

    fun savePlan(
        existing: RecurringExpense?,
        name: String,
        amountMinor: Long,
        categoryName: String,
        bankName: String,
        dayOfMonth: Int,
        frequency: Frequency,
        intervalDays: Int,
        accountId: Long?,
        isSubscription: Boolean,
        reminderDaysBefore: Int
    ) {
        viewModelScope.launch {
            val nextDue = if (frequency == Frequency.MONTHLY) {
                firstDueDateForDayOfMonth(System.currentTimeMillis(), dayOfMonth)
            } else {
                existing?.nextDueDate?.takeIf { it > 0 }
                    ?: com.localexpense.tracker.domain.nextDueDate(
                        System.currentTimeMillis(), frequency, intervalDays
                    )
            }

            val item = (existing ?: RecurringExpense(
                amountMinor = amountMinor,
                merchant = name,
                bankName = bankName,
                categoryName = categoryName,
                dayOfMonth = dayOfMonth,
                lastAddedMonth = ""
            )).copy(
                amountMinor = amountMinor,
                merchant = name,
                name = name,
                bankName = bankName,
                categoryName = categoryName,
                dayOfMonth = dayOfMonth,
                frequency = frequency,
                intervalDays = intervalDays,
                accountId = accountId,
                isSubscription = isSubscription,
                reminderDaysBefore = reminderDaysBefore,
                nextDueDate = nextDue
            )

            if (item.id == 0L) repository.insertRecurringExpense(item) else repository.updateRecurringExpense(item)
            refreshUpcoming()
        }
    }

    /** إيقاف مؤقت (pause) واستئناف — نفس الفلاج بيغطي "ألغِ الاشتراك" برضه. */
    fun setActive(item: RecurringExpense, active: Boolean) {
        viewModelScope.launch {
            repository.setRecurringActive(item, active)
            refreshUpcoming()
        }
    }

    fun delete(item: RecurringExpense) {
        viewModelScope.launch {
            repository.deleteRecurringExpense(item)
            refreshUpcoming()
        }
    }

    // ===== الأقساط =====

    fun saveInstallment(installment: Installment) {
        viewModelScope.launch {
            repository.saveInstallment(installment)
            refreshUpcoming()
        }
    }

    fun deleteInstallment(installment: Installment) {
        viewModelScope.launch {
            repository.deleteInstallment(installment)
            refreshUpcoming()
        }
    }

    /** تسجيل قسط مدفوع — بيضيف حركة بمبلغ القسط بس (مش إجمالي المشترى). */
    fun payInstallment(installment: Installment) {
        viewModelScope.launch {
            repository.payInstallment(installment)
            refreshUpcoming()
        }
    }
}
