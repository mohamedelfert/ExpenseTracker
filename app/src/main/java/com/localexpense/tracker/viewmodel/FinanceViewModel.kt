package com.localexpense.tracker.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localexpense.tracker.data.AppSettings
import com.localexpense.tracker.data.DayTotal
import com.localexpense.tracker.data.ExpenseRepository
import com.localexpense.tracker.domain.Assistant
import com.localexpense.tracker.domain.FinancialContext
import com.localexpense.tracker.domain.Insight
import com.localexpense.tracker.domain.MonthSummary
import com.localexpense.tracker.domain.forecast
import com.localexpense.tracker.domain.generateInsights
import com.localexpense.tracker.util.CsvExporter
import com.localexpense.tracker.util.ExportSink
import com.localexpense.tracker.util.PdfReport
import com.localexpense.tracker.util.monthLabel
import com.localexpense.tracker.util.monthRange
import com.localexpense.tracker.util.monthRangeOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** سؤال وجواب في شاشة المساعد (المرحلة 19) — محلي بالكامل. */
data class AssistantMessage(val question: String, val answer: String)

/**
 * محرّك التحليلات لواجهة المستخدم: الداشبورد، المقارنة، التوقّع، الرؤى،
 * التقارير، والمساعد — كلهم بيقروا من نفس [FinancialContext] فمفيش شاشة
 * بتعرض رقم مختلف عن التانية.
 */
class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExpenseRepository(application)
    private val settings = AppSettings(application)

    /** 0 = الشهر الحالي، -1 = الشهر اللي فات... */
    private val _monthOffset = MutableStateFlow(0)
    val monthOffset: StateFlow<Int> = _monthOffset.asStateFlow()

    private val _context = MutableStateFlow<FinancialContext?>(null)
    val financialContext: StateFlow<FinancialContext?> = _context.asStateFlow()

    private val _insights = MutableStateFlow<List<Insight>>(emptyList())
    val insights: StateFlow<List<Insight>> = _insights.asStateFlow()

    private val _assistantMessages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val assistantMessages: StateFlow<List<AssistantMessage>> = _assistantMessages.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    /** الرسم البياني اليومي للشهر المعروض. */
    val dailyTotals: StateFlow<List<DayTotal>> = monthRange().let { range ->
        repository.observeDailyTotalsBetween(range.start, range.end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** الدخل اليومي — التقويم بيعرضه جنب الصرف (المرحلة 15). */
    val dailyIncome: StateFlow<List<DayTotal>> = monthRange().let { range ->
        repository.observeDailyIncomeBetween(range.start, range.end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val context = withContext(Dispatchers.IO) {
                repository.buildFinancialContext(_monthOffset.value)
            }
            _context.value = context
            val dismissed = settings.dismissedInsights
            _insights.value = generateInsights(context).filter { it.key !in dismissed }
        }
    }

    fun showMonth(offset: Int) {
        _monthOffset.value = offset.coerceAtMost(0)
        refresh()
    }

    fun dismissInsight(insight: Insight) {
        settings.dismissInsight(insight.key)
        _insights.value = _insights.value.filterNot { it.key == insight.key }
    }

    fun restoreDismissedInsights() {
        settings.clearDismissedInsights()
        refresh()
    }

    // ===== المساعد المحلي (المرحلة 19) =====

    fun ask(question: String) {
        val context = _context.value ?: return
        if (question.isBlank()) return
        // الجواب بيتبني من أرقام محسوبة سلفًا بس — راجع domain/Assistant.
        val answer = Assistant.answer(question, context)
        _assistantMessages.value = _assistantMessages.value + AssistantMessage(question, answer)
    }

    fun clearAssistant() {
        _assistantMessages.value = emptyList()
    }

    // ===== التقارير (المرحلة 18، بند 40) =====

    enum class ReportKind(val label: String) {
        MONTHLY("مصروفات الشهر"),
        CATEGORIES("حسب الفئة"),
        MERCHANTS("حسب الجهة"),
        INCOME_VS_EXPENSE("الدخل مقابل المصروف"),
        BUDGETS("أداء الميزانيات"),
        SUBSCRIPTIONS("الاشتراكات"),
        INSTALLMENTS("الأقساط")
    }

    /**
     * تسليم الملف المصدَّر.
     *
     * [uri] = الملف اللي المستخدم اختاره من منتقي النظام، أو null لو المنتقي
     * مش متاح على الجهاز. في الحالتين مفيش استثناء بيطلع للـ coroutine: أي
     * فشل بيرجع كرسالة للمستخدم، لأن استثناء جوه viewModelScope بيقفل التطبيق.
     */
    private suspend fun deliver(uri: Uri?, fileName: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            if (uri != null) {
                val wrote = runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(bytes)
                    } ?: error("تعذّر فتح الملف للكتابة")
                }
                if (wrote.isSuccess) return@withContext "تم التصدير بنجاح"
            }

            val path = ExportSink.write(getApplication(), fileName, bytes)
            if (path != null) {
                "منتقي الملفات مش متاح على الجهاز، فالملف اتحفظ في:\n$path"
            } else {
                "فشل التصدير: مفيش مكان متاح للكتابة"
            }
        }

    /**
     * تقرير مجمّع بصيغة CSV. [uri] = null معناها منتقي الملفات مش متاح،
     * فالملف بيروح للمكان الاحتياطي.
     */
    fun exportCsvReport(uri: Uri?, kind: ReportKind) {
        viewModelScope.launch {
            _exportMessage.value = try {
                val rows = withContext(Dispatchers.IO) { buildReportRows(kind) }
                val text = CsvExporter.aggregateToCsv(kind.label, rows)
                deliver(uri, "${kind.name.lowercase()}.csv", text.toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                "فشل تصدير \"${kind.label}\": ${e.message ?: "خطأ غير متوقع"}"
            }
        }
    }

    fun exportTransactionsCsv(uri: Uri?) {
        viewModelScope.launch {
            _exportMessage.value = try {
                val text = withContext(Dispatchers.IO) {
                    val range = monthRangeOffset(_monthOffset.value)
                    val expenses = repository.searchOnce(
                        com.localexpense.tracker.data.TransactionFilter(
                            startTime = range.start,
                            endTime = range.end,
                            limit = 100_000
                        )
                    )
                    CsvExporter.exportToCsv(
                        expenses = expenses,
                        includeRawText = settings.includeRawTextInExport
                    )
                }
                deliver(uri, "transactions.csv", text.toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                "فشل تصدير الحركات: ${e.message ?: "خطأ غير متوقع"}"
            }
        }
    }

    fun exportPdfReport(uri: Uri?) {
        viewModelScope.launch {
            val context = _context.value
            if (context == null) {
                _exportMessage.value = "أرقام الشهر لسه بتتحسب، جرّب تاني بعد لحظة"
                return@launch
            }

            _exportMessage.value = try {
                val bytes = withContext(Dispatchers.IO) {
                    PdfReport.render(
                        title = "تقرير مالي - ${context.monthLabel}",
                        subtitle = "تم إنشاؤه محليًا على الجهاز",
                        sections = listOf(
                            PdfReport.Section(
                                "الملخص",
                                listOf(
                                    "الدخل" to context.summary.incomeMinor,
                                    "المصروفات" to context.summary.expenseMinor,
                                    "الاسترداد" to context.summary.refundMinor,
                                    "الصافي" to context.summary.netCashFlowMinor,
                                    "المتوقّع بنهاية الشهر" to context.forecast.projectedMinor
                                )
                            ),
                            PdfReport.Section(
                                "حسب الفئة",
                                context.categoryTotals.toList().sortedByDescending { it.second }
                            ),
                            PdfReport.Section("أعلى الجهات", context.topMerchants),
                            PdfReport.Section(
                                "الميزانيات",
                                context.categoryBudgetProgress.map { (name, progress) ->
                                    "$name (${progress.percentUsed}%)" to progress.spentMinor
                                }
                            ),
                            PdfReport.Section(
                                "الالتزامات الشهرية",
                                listOf(
                                    "اشتراكات" to context.subscriptionsMonthlyMinor,
                                    "أقساط" to context.installmentsMonthlyMinor
                                )
                            )
                        )
                    )
                }
                deliver(uri, "financial-report.pdf", bytes)
            } catch (e: Exception) {
                "فشل تصدير PDF: ${e.message ?: "خطأ غير متوقع"}"
            }
        }
    }

    private suspend fun buildReportRows(kind: ReportKind): List<Pair<String, Long>> {
        val context = _context.value ?: repository.buildFinancialContext(_monthOffset.value)
        return when (kind) {
            ReportKind.MONTHLY -> (0 downTo -5).map { offset ->
                val range = monthRangeOffset(offset)
                val summary = repository.monthSummary(range)
                monthLabel(range.start) to summary.netSpentMinor
            }
            ReportKind.CATEGORIES -> context.categoryTotals.toList().sortedByDescending { it.second }
            ReportKind.MERCHANTS -> context.topMerchants
            ReportKind.INCOME_VS_EXPENSE -> listOf(
                "الدخل" to context.summary.incomeMinor,
                "المصروفات" to context.summary.expenseMinor,
                "الاسترداد" to context.summary.refundMinor,
                "الصافي" to context.summary.netCashFlowMinor
            )
            ReportKind.BUDGETS -> context.categoryBudgetProgress.map { (name, progress) ->
                "$name (${progress.percentUsed}% من ${progress.limitMinor / 100})" to progress.spentMinor
            }
            ReportKind.SUBSCRIPTIONS -> listOf("إجمالي الاشتراكات الشهرية" to context.subscriptionsMonthlyMinor)
            ReportKind.INSTALLMENTS -> listOf("إجمالي الأقساط الشهرية" to context.installmentsMonthlyMinor)
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    // ===== المقارنة الشهرية (المرحلة 9) =====

    /** ملخص شهر معيّن بالإزاحة — لشاشة المقارنة. */
    suspend fun summaryForOffset(offset: Int): MonthSummary =
        repository.monthSummary(monthRangeOffset(offset))

    /** إعادة حساب التوقّع بميزانية مقترحة (بدون تخزين) — لعرض "لو حددت X". */
    fun previewForecast(budgetLimitMinor: Long) = _context.value?.let { context ->
        forecast(
            netSpentMinor = context.summary.netSpentMinor,
            daysElapsed = context.forecast.daysElapsed,
            daysInMonth = context.forecast.daysElapsed + context.forecast.daysRemaining,
            budgetLimitMinor = budgetLimitMinor
        )
    }
}
