package com.localexpense.tracker.util

import android.content.Context
import java.io.File

/**
 * مكان حفظ احتياطي للملفات المصدَّرة.
 *
 * التصدير الأساسي بيمر على منتقي ملفات النظام (SAF)، لكن في بعض الأجهزة
 * (روم معدّلة، أو DocumentsUI متعطّل/مشال) مفيش تطبيق بيستقبل
 * ACTION_CREATE_DOCUMENT خالص، و`launch()` بترمي ActivityNotFoundException —
 * وده كان بيقفل التطبيق بمجرد الضغط على زرار التصدير.
 *
 * الحل: نكتب الملف في مجلد التطبيق الخارجي. مش محتاج أي إذن على أي إصدار،
 * والمستخدم يقدر يوصله من أي مدير ملفات على المسار المعروض له.
 */
object ExportSink {

    private const val DIR_NAME = "exports"

    /** بيرجّع المسار الكامل لو نجح، و null لو فشل. */
    fun write(context: Context, fileName: String, bytes: ByteArray): String? = runCatching {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, DIR_NAME).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        file.absolutePath
    }.getOrNull()
}
