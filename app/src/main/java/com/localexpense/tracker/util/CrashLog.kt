package com.localexpense.tracker.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * سجل آخر انهيار (crash) في ملف محلي جوه التطبيق.
 *
 * التطبيق بيتوزّع كـ APK بره المتجر ومفيش فيه أي اتصال بالإنترنت، فمفيش
 * Crashlytics ولا أي تقرير تلقائي. لما التطبيق يقفل فجأة، المستخدم مش عنده أي
 * طريقة يعرف السبب. الملف ده بيتكتب محليًا بس، والمستخدم هو اللي بيقرر
 * يشاركه من شاشة الإعدادات (زرار "نسخ") — مفيش أي إرسال تلقائي لأي حاجة.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    /**
     * أخطاء مش قاتلة (التطبيق كمّل شغل): بتتكتب في ملف تاني عشان متمسحش
     * تقرير الانهيار الحقيقي من التشغيلة اللي فاتت قبل ما المستخدم يقراه.
     */
    private const val ERROR_FILE_NAME = "last_error.txt"
    private const val MAX_CHARS = 12_000

    /** بيتركّب مرة واحدة في [com.localexpense.tracker.ExpenseApp]. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            // مهم: بنكمّل للـ handler الأصلي عشان النظام يعمل اللي بيعمله
            // عادة (يقفل العملية ويسجّل في logcat) - مش بنخفي الانهيار.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        File(context.filesDir, FILE_NAME).writeText(report(thread.name, throwable))
    }

    /**
     * بيسجّل خطأ **مش قاتل** - التطبيق بيفضل شغّال. الفايدة: لو فتح قاعدة
     * البيانات فشل مثلاً، التطبيق بيفتح عادي والسبب بيبان في شاشة الإعدادات
     * بدل ما التطبيق يقفل في وش المستخدم من غير أي معلومة.
     *
     * ملفوفة في runCatching لأن دالة تسجيل الأخطاء نفسها مينفعش تكون سبب
     * انهيار جديد.
     */
    fun recordNonFatal(context: Context, label: String, throwable: Throwable) {
        runCatching {
            File(context.applicationContext.filesDir, ERROR_FILE_NAME)
                .writeText(report(label, throwable))
        }
    }

    private fun report(label: String, throwable: Throwable): String {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("time: $timestamp")
            appendLine("where: $label")
            appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("android: ${android.os.Build.VERSION.SDK_INT}")
            appendLine()
            append(stack)
        }.take(MAX_CHARS)
    }

    /** بيرجع التقريرين مع بعض (انهيار + خطأ غير قاتل) لو الاتنين موجودين. */
    fun read(context: Context): String? {
        val dir = context.applicationContext.filesDir
        val sections = listOf(
            "انهيار" to File(dir, FILE_NAME),
            "خطأ (التطبيق كمّل)" to File(dir, ERROR_FILE_NAME)
        ).mapNotNull { (title, file) ->
            if (!file.exists()) return@mapNotNull null
            val body = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            "===== $title =====\n$body"
        }
        return sections.joinToString("\n\n").ifBlank { null }
    }

    fun clear(context: Context) {
        val dir = context.applicationContext.filesDir
        runCatching { File(dir, FILE_NAME).delete() }
        runCatching { File(dir, ERROR_FILE_NAME).delete() }
    }
}
