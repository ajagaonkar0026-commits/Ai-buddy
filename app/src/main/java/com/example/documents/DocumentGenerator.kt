package com.example.documents

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ResumeInput(
    val fullName: String,
    val roleTitle: String,
    val email: String,
    val phone: String,
    val summary: String,
    val skills: String,
    val experience: String,
    val education: String
)

data class GeneratedDocumentResult(
    val fileName: String,
    val mimeType: String,
    val shareUri: Uri,
    val previewBitmap: Bitmap? = null
)

class DocumentGenerator(private val context: Context) {

    suspend fun generateResumePdf(input: ResumeInput): Result<GeneratedDocumentResult> = withContext(Dispatchers.IO) {
        try {
            val pdf = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            // Header Banner
            val headerPaint = Paint().apply { color = Color.rgb(14, 21, 37) }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 132f, headerPaint)
            val accentPaint = Paint().apply { color = Color.rgb(0, 229, 255) }
            canvas.drawRect(0f, 128f, pageWidth.toFloat(), 134f, accentPaint)

            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 229, 255)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225)
                textSize = 11f
            }

            canvas.drawText(input.fullName.ifBlank { "Candidate Name" }, 36f, 52f, namePaint)
            canvas.drawText(input.roleTitle.ifBlank { "Professional Role" }, 36f, 76f, rolePaint)
            val contactLine = listOf(input.email, input.phone).filter { it.isNotBlank() }.joinToString("  •  ")
            if (contactLine.isNotBlank()) {
                canvas.drawText(contactLine, 36f, 102f, contactPaint)
            }

            var cursorY = 160f
            cursorY = drawSection(canvas, "PROFESSIONAL SUMMARY", input.summary.ifBlank { "Results-driven specialist." }, cursorY, pageWidth)
            cursorY = drawSection(canvas, "CORE SKILLS", input.skills.ifBlank { "Strategy, Communication, Execution" }, cursorY + 12f, pageWidth)
            cursorY = drawSection(canvas, "EXPERIENCE", input.experience.ifBlank { "Experience details provided upon request." }, cursorY + 12f, pageWidth)
            drawSection(canvas, "EDUCATION", input.education.ifBlank { "Academic credentials." }, cursorY + 12f, pageWidth)

            pdf.finishPage(page)

            val fileName = "Resume_${input.fullName.replace(" ", "_").ifBlank { "Jarvis" }}_${System.currentTimeMillis()}.pdf"
            val outFile = File(context.cacheDir, fileName)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()

            saveToDownloads(outFile, fileName, "application/pdf")
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val preview = renderFirstPdfPage(outFile)
            Result.success(GeneratedDocumentResult(fileName, "application/pdf", shareUri, preview))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateReportPdf(title: String, body: String): Result<GeneratedDocumentResult> = withContext(Dispatchers.IO) {
        try {
            val pdf = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val headerPaint = Paint().apply { color = Color.rgb(7, 11, 20) }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, headerPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 229, 255)
                textSize = 11f
            }
            canvas.drawText(title.ifBlank { "Jarvis Report" }.take(46), 36f, 48f, titlePaint)
            canvas.drawText("Generated ${android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", System.currentTimeMillis())}", 36f, 72f, subPaint)

            drawSection(canvas, "REPORT CONTENT", body.ifBlank { "No content provided." }, 124f, pageWidth)
            pdf.finishPage(page)

            val fileName = "Jarvis_Report_${System.currentTimeMillis()}.pdf"
            val outFile = File(context.cacheDir, fileName)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()

            saveToDownloads(outFile, fileName, "application/pdf")
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val preview = renderFirstPdfPage(outFile)
            Result.success(GeneratedDocumentResult(fileName, "application/pdf", shareUri, preview))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateWordCompatibleDoc(title: String, body: String): Result<GeneratedDocumentResult> = withContext(Dispatchers.IO) {
        try {
            val html = """
                <html><head><meta charset="utf-8"><title>$title</title></head>
                <body style="font-family:sans-serif;padding:24px;">
                <h1 style="color:#0E1525;">$title</h1>
                <p style="white-space:pre-line;line-height:1.5;">$body</p>
                </body></html>
            """.trimIndent()
            val fileName = "Jarvis_Doc_${System.currentTimeMillis()}.doc"
            val outFile = File(context.cacheDir, fileName)
            outFile.writeText(html)
            saveToDownloads(outFile, fileName, "application/msword")
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            Result.success(GeneratedDocumentResult(fileName, "application/msword", shareUri, null))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateExcelCsv(title: String, rows: List<List<String>>): Result<GeneratedDocumentResult> = withContext(Dispatchers.IO) {
        try {
            val csv = buildString {
                rows.forEach { row ->
                    appendLine(row.joinToString(",") { cell -> "\"${cell.replace("\"", "\"\"")}\"" })
                }
            }
            val fileName = "${title.replace(" ", "_").ifBlank { "Jarvis_Sheet" }}_${System.currentTimeMillis()}.csv"
            val outFile = File(context.cacheDir, fileName)
            outFile.writeText(csv)
            saveToDownloads(outFile, fileName, "text/csv")
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            Result.success(GeneratedDocumentResult(fileName, "text/csv", shareUri, null))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectPickedDocument(uri: Uri): Pair<String, Bitmap?> = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        var preview: Bitmap? = null
        val textContent = StringBuilder()

        if (mime.contains("pdf")) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        textContent.append("PDF Document • ${renderer.pageCount} page(s).")
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                preview = bmp
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                textContent.append("Unable to render PDF: ${e.localizedMessage}")
            }
        } else {
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    textContent.append(reader.readText().take(4000))
                }
            } catch (e: Exception) {
                textContent.append("Unable to read document text: ${e.localizedMessage}")
            }
        }
        textContent.toString() to preview
    }

    fun shareDocument(result: GeneratedDocumentResult) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, result.shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${result.fileName}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun drawSection(canvas: android.graphics.Canvas, heading: String, body: String, startY: Float, pageWidth: Int): Float {
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(14, 21, 37)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(0, 172, 193)
            strokeWidth = 1.5f
        }
        canvas.drawText(heading, 36f, startY, headingPaint)
        canvas.drawLine(36f, startY + 5f, pageWidth - 36f, startY + 5f, linePaint)

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 11f
        }
        val layout = StaticLayout.Builder.obtain(body, 0, body.length, bodyPaint, pageWidth - 72)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.15f)
            .build()
        canvas.save()
        canvas.translate(36f, startY + 14f)
        layout.draw(canvas)
        canvas.restore()
        return startY + 18f + layout.height
    }

    private fun renderFirstPdfPage(file: File): Bitmap? {
        return try {
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    } else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveToDownloads(file: File, fileName: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/JarvisDocs")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
        }
    }
}
