package com.localexpense.tracker.util

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.localexpense.tracker.money.formatMinor

/**
 * تقرير PDF (المرحلة 18، بند 40) باستخدام `android.graphics.pdf.PdfDocument`
 * اللي جوه أندرويد نفسه — مفيش أي مكتبة PDF اتضافت.
 *
 * ponytail: تخطيط بسيط (عنوان + سطور "اسم ... مبلغ")، بيكفي لتقرير مالي
 * ينفع يتطبع أو يتبعت. لو احتاج جداول وألوان ورسوم بيانية بعدين، ساعتها بس
 * يستحق مكتبة.
 */
object PdfReport {

    data class Section(val title: String, val rows: List<Pair<String, Long>>)

    private const val PAGE_WIDTH = 595   // A4 بـ 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun write(
        context: Context,
        uri: Uri,
        title: String,
        subtitle: String,
        sections: List<Section>
    ) {
        val document = PdfDocument()
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val headerPaint = Paint().apply { textSize = 14f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 12f }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN + 20f

        canvas.drawText(title, MARGIN, y, titlePaint)
        y += 22f
        canvas.drawText(subtitle, MARGIN, y, bodyPaint)
        y += 28f

        for (section in sections) {
            // صفحة جديدة لو مفيش مساحة للعنوان + سطرين على الأقل
            if (y > PAGE_HEIGHT - MARGIN - 60f) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                canvas = page.canvas
                y = MARGIN + 20f
            }

            canvas.drawText(section.title, MARGIN, y, headerPaint)
            y += 18f

            if (section.rows.isEmpty()) {
                canvas.drawText("لا توجد بيانات", MARGIN + 12f, y, bodyPaint)
                y += 16f
            }

            for ((label, amountMinor) in section.rows) {
                if (y > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    )
                    canvas = page.canvas
                    y = MARGIN + 20f
                }
                canvas.drawText(label.take(48), MARGIN + 12f, y, bodyPaint)
                val amount = formatMinor(amountMinor)
                canvas.drawText(amount, PAGE_WIDTH - MARGIN - bodyPaint.measureText(amount), y, bodyPaint)
                y += 16f
            }
            y += 12f
        }

        document.finishPage(page)
        context.contentResolver.openOutputStream(uri, "wt")?.use { document.writeTo(it) }
        document.close()
    }
}
