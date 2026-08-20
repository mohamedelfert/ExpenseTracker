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
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            appendLine("time: $timestamp")
            appendLine("thread: ${thread.name}")
            appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("android: ${android.os.Build.VERSION.SDK_INT}")
            appendLine()
            append(stack)
        }.take(MAX_CHARS)

        File(context.filesDir, FILE_NAME).writeText(text)
    }

    fun read(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
    }
}
