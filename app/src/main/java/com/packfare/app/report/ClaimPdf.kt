package com.packfare.app.report

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.packfare.app.domain.ClaimSummary
import com.packfare.app.domain.Money
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Who is claiming what from whom; printed at the top of the PDF. */
data class ClaimHeader(
    val reference: String,
    val eventName: String,
    val place: String,
    val claimant: String,
    val company: String,
    val periodFrom: LocalDate?,
    val periodTo: LocalDate?,
)

/**
 * Builds a plain, printable A4 expense claim: header, itemised table, category totals.
 * Uses the platform PdfDocument, so there is no extra library and it works offline.
 */
object ClaimPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    private val ink = Color.rgb(0x1F, 0x2A, 0x24)
    private val green = Color.rgb(0x2F, 0x6B, 0x4F)
    private val grey = Color.rgb(0x6B, 0x72, 0x6E)
    private val rule = Color.rgb(0xCF, 0xD6, 0xD1)

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink, right: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (right) textAlign = Paint.Align.RIGHT
        }

    /** "₹" is missing from some PDF viewers' default font, so the document says "Rs." instead. */
    private fun rs(paise: Long) = "Rs. " + Money.format(paise).removePrefix("₹").removePrefix("-₹")
        .let { if (paise < 0) "-$it" else it }

    private fun clip(text: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    fun write(file: File, header: ClaimHeader, summary: ClaimSummary) {
        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        var y = 0f

        fun startPage(first: Boolean) {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            val c = page.canvas
            y = M
            if (first) {
                c.drawText("Expense claim", M, y + 20, paint(22f, bold = true, color = green))
                c.drawText(header.reference, W - M, y + 20, paint(11f, color = grey, right = true))
                y += 40
                fun row(label: String, value: String) {
                    if (value.isBlank()) return
                    c.drawText(label, M, y + 12, paint(10f, color = grey))
                    c.drawText(clip(value, paint(11f), W - M * 2 - 90), M + 90, y + 12, paint(11f))
                    y += 18
                }
                row("Event / trip", header.eventName)
                row("Place", header.place)
                row("Claimant", header.claimant)
                row("Company", header.company)
                val period = when {
                    header.periodFrom == null -> ""
                    header.periodTo == null || header.periodTo == header.periodFrom -> header.periodFrom.format(dateFmt)
                    else -> header.periodFrom.format(dateFmt) + " to " + header.periodTo.format(dateFmt)
                }
                row("Period", period)
                row("Prepared", LocalDate.now().format(dateFmt))
                y += 8
            } else {
                c.drawText("Expense claim ${header.reference} (continued)", M, y + 12, paint(10f, color = grey))
                y += 26
            }
            // Table header
            val hp = paint(9.5f, bold = true, color = grey)
            c.drawText("#", M, y + 10, hp)
            c.drawText("Date", M + 24, y + 10, hp)
            c.drawText("Vendor / description", M + 90, y + 10, hp)
            c.drawText("Category", M + 330, y + 10, hp)
            c.drawText("Paid by", M + 395, y + 10, hp)
            c.drawText("Amount", W - M, y + 10, paint(9.5f, bold = true, color = grey, right = true))
            y += 16
            c.drawLine(M, y, W - M, y, Paint().apply { color = rule; strokeWidth = 1f })
            y += 4
        }

        startPage(first = true)
        val body = paint(10.5f)
        val amount = paint(10.5f, right = true)
        summary.lines.forEachIndexed { i, line ->
            if (y > H - M - 40) {
                doc.finishPage(page)
                startPage(first = false)
            }
            val c = page.canvas
            val mark = if (line.hasReceipt) "" else " *"
            c.drawText("${i + 1}", M, y + 14, body)
            c.drawText(LocalDate.ofEpochDay(line.epochDay).format(dateFmt), M + 24, y + 14, body)
            c.drawText(clip(line.vendor + mark, body, 232f), M + 90, y + 14, body)
            c.drawText(line.category.label, M + 330, y + 14, body)
            c.drawText(line.paymentMethod.label, M + 395, y + 14, body)
            c.drawText(rs(line.amountPaise), W - M, y + 14, amount)
            y += 20
        }

        // Totals block needs ~ (categories + 4) lines; keep it together.
        val needed = 50f + summary.byCategory.size * 16f + 60f
        if (y + needed > H - M) {
            doc.finishPage(page)
            startPage(first = false)
        }
        var c = page.canvas
        y += 6
        c.drawLine(M, y, W - M, y, Paint().apply { color = ink; strokeWidth = 1.2f })
        y += 22
        c.drawText("Total claimed", M + 90, y, paint(13f, bold = true))
        c.drawText(rs(summary.totalPaise), W - M, y, paint(13f, bold = true, right = true))
        y += 30
        c.drawText("By category", M + 90, y, paint(10.5f, bold = true, color = green))
        y += 16
        summary.byCategory.forEach { (cat, total) ->
            c.drawText(cat.label, M + 90, y, body)
            c.drawText(rs(total), W - M, y, amount)
            y += 16
        }
        y += 10
        c.drawText(
            "Paid by: " + summary.byMethod.joinToString("   ") { (m, t) -> "${m.label} ${rs(t)}" },
            M + 90, y, paint(9.5f, color = grey),
        )
        y += 16
        if (summary.withoutReceipt > 0) {
            c.drawText(
                "* ${summary.withoutReceipt} bill(s) have no receipt photo in the app.",
                M + 90, y, paint(9.5f, color = grey),
            )
        }
        c.drawText("Generated with Packfare", W / 2f, H - 22f, paint(8.5f, color = grey).apply { textAlign = Paint.Align.CENTER })
        doc.finishPage(page)

        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    /** Writes the PDF into the cache and opens the Android share sheet (email, Drive, WhatsApp...). */
    fun shareFile(context: Context, file: File, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Send claim").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun outputFile(context: Context, reference: String) = File(File(context.cacheDir, "claims"), "$reference.pdf")
}
