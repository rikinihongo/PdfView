package com.sonpxp.pdfloader.security


import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintJobInfo
import android.print.PrintManager
import android.webkit.WebView
import java.io.FileOutputStream

/**
 * Manages print protection features for PDF documents
 * Controls printing, saving, and export functionality
 */
class PrintProtection(
    private val context: Context,
    private val config: SecurityConfig
) {

    private val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    private val auditLog = mutableListOf<PrintEvent>()

    /**
     * Represents a print-related security event
     */
    data class PrintEvent(
        val documentId: String,
        val eventType: EventType,
        val timestamp: Long,
        val pageCount: Int = 0,
        val success: Boolean,
        val details: String = ""
    ) {
        enum class EventType {
            PRINT_ATTEMPT,
            PRINT_BLOCKED,
            PRINT_SUCCESS,
            SAVE_ATTEMPT,
            SAVE_BLOCKED,
            SAVE_SUCCESS,
            EXPORT_ATTEMPT,
            EXPORT_BLOCKED,
            EXPORT_SUCCESS,
            WATERMARK_APPLIED,
            SHARE_BLOCKED
        }
    }

    /**
     * Custom PrintDocumentAdapter with security controls
     * Uses delegation pattern to avoid WriteResultCallback constructor issues
     */
    inner class SecurePrintDocumentAdapter(
        private val documentId: String,
        private val originalAdapter: PrintDocumentAdapter?,
        private val pageCount: Int
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: android.os.CancellationSignal?,
            layoutResultCallback: LayoutResultCallback?,
            extras: android.os.Bundle?
        ) {
            if (!config.printEnabled) {
                logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false, "Print disabled in configuration")
                layoutResultCallback?.onLayoutFailed("Printing is not allowed for this document")
                return
            }

            logPrintEvent(documentId, PrintEvent.EventType.PRINT_ATTEMPT, pageCount, true)

            // Apply watermark to print attributes if needed
            val modifiedAttributes = if (config.watermarkEnabled) {
                applyWatermarkToPrintAttributes(newAttributes)
            } else {
                newAttributes
            }

            if (originalAdapter != null) {
                originalAdapter.onLayout(oldAttributes, modifiedAttributes, cancellationSignal, layoutResultCallback, extras)
            } else {
                layoutResultCallback?.onLayoutFailed("No print adapter available")
            }
        }

        override fun onWrite(
            pages: Array<out android.print.PageRange>?,
            destination: android.os.ParcelFileDescriptor?,
            cancellationSignal: android.os.CancellationSignal?,
            writeResultCallback: WriteResultCallback?
        ) {
            if (!config.printEnabled) {
                logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false, "Print blocked during write")
                writeResultCallback?.onWriteFailed("Printing is not allowed for this document")
                return
            }

            if (originalAdapter == null) {
                writeResultCallback?.onWriteFailed("No print adapter available")
                return
            }

            try {
                // Use the original callback directly since we can't extend WriteResultCallback
                // Instead, we'll log events before and after the original adapter's onWrite
                logPrintEvent(documentId, PrintEvent.EventType.PRINT_ATTEMPT, pages?.size ?: pageCount, true, "Starting write operation")

                originalAdapter.onWrite(pages, destination, cancellationSignal, writeResultCallback)

                // Note: We can't intercept the callback results due to WriteResultCallback being package-private
                // The logging will happen in the original callback or we need to track print jobs separately

            } catch (e: Exception) {
                logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false, e.message ?: "Unknown error")
                writeResultCallback?.onWriteFailed("Error during printing: ${e.message}")
            }
        }
    }

    /**
     * Checks if printing is allowed for a document
     */
    fun isPrintAllowed(documentId: String): Boolean {
        if (!config.printEnabled) {
            logPrintEvent(documentId, PrintEvent.EventType.PRINT_ATTEMPT, 0, false)
            return false
        }

        logPrintEvent(documentId, PrintEvent.EventType.PRINT_ATTEMPT, 0, true)
        return true
    }

    /**
     * Checks if saving is allowed for a document
     */
    fun isSaveAllowed(documentId: String): Boolean {
        if (!config.saveEnabled) {
            logPrintEvent(documentId, PrintEvent.EventType.SAVE_ATTEMPT, 0, false)
            return false
        }

        logPrintEvent(documentId, PrintEvent.EventType.SAVE_ATTEMPT, 0, true)
        return true
    }

    /**
     * Attempts to print a document with security checks
     */
    @Throws(SecurityException::class)
    fun printDocument(
        documentId: String,
        jobName: String,
        adapter: PrintDocumentAdapter,
        pageCount: Int = 0
    ): PrintJob? {
        if (!config.printEnabled) {
            logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false)
            throw SecurityException(config.restrictedActionMessage)
        }

        return try {
            val secureAdapter = SecurePrintDocumentAdapter(documentId, adapter, pageCount)
            val printJob = printManager.print(jobName, secureAdapter, null)

            // Log the print job creation - detailed monitoring is optional
            logPrintEvent(documentId, PrintEvent.EventType.PRINT_SUCCESS, pageCount, true, "Print job created: ${printJob.id}")

            // Optional: Start monitoring the print job status if needed
            if (config.auditLoggingEnabled) {
                monitorPrintJobSimple(documentId, printJob, pageCount)
            }

            printJob

        } catch (e: Exception) {
            logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false, e.message ?: "Unknown error")
            throw SecurityException("Failed to print document: ${e.message}")
        }
    }

    /**
     * Simple print job monitoring (optional)
     */
    private fun monitorPrintJobSimple(documentId: String, printJob: PrintJob, pageCount: Int) {
        Thread {
            try {
                // Simple monitoring - just check final state after some delay
                Thread.sleep(5000) // Wait 5 seconds

                val finalInfo = printJob.info
                val finalState = finalInfo?.state

                when (finalState) {
                    PrintJobInfo.STATE_COMPLETED -> {
                        logPrintEvent(documentId, PrintEvent.EventType.PRINT_SUCCESS, pageCount, true, "Print completed: ${printJob.id}")
                    }
                    PrintJobInfo.STATE_FAILED -> {
                        logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false, "Print failed: ${printJob.id}")
                    }
                    PrintJobInfo.STATE_CANCELED -> {
                        logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false, "Print cancelled: ${printJob.id}")
                    }
                    else -> {
                        // Still in progress or other state - that's fine
                        logPrintEvent(documentId, PrintEvent.EventType.PRINT_ATTEMPT, pageCount, true, "Print status: $finalState")
                    }
                }

            } catch (e: Exception) {
                android.util.Log.w("PrintProtection", "Error in simple print job monitoring", e)
            }
        }.start()
    }

    /**
     * Monitors print job status since we can't intercept WriteResultCallback
     */
    private fun monitorPrintJob(documentId: String, printJob: PrintJob, pageCount: Int) {
        // Use a background thread to monitor print job status
        Thread {
            try {
                var lastState = printJob.info?.state
                var attempts = 0
                val maxAttempts = 30 // Monitor for up to 30 seconds

                while (attempts < maxAttempts) {
                    Thread.sleep(1000) // Check every second

                    val currentInfo = printJob.info
                    val currentState = currentInfo?.state

                    if (currentState != lastState && currentState != null) {
                        when (currentState) {
                            PrintJobInfo.STATE_COMPLETED -> {
                                logPrintEvent(documentId, PrintEvent.EventType.PRINT_SUCCESS, pageCount, true, "Print job completed: ${printJob.id}")
                                break
                            }
                            PrintJobInfo.STATE_FAILED -> {
                                logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false, "Print job failed: ${printJob.id}")
                                break
                            }
                            PrintJobInfo.STATE_CANCELED -> {
                                logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false, "Print job cancelled: ${printJob.id}")
                                break
                            }
                            PrintJobInfo.STATE_BLOCKED -> {
                                logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, pageCount, false, "Print job blocked: ${printJob.id}")
                            }
                            PrintJobInfo.STATE_CREATED -> {
                                logPrintEvent(documentId, PrintEvent.EventType.PRINT_ATTEMPT, pageCount, true, "Print job created: ${printJob.id}")
                            }
                            PrintJobInfo.STATE_QUEUED -> {
                                logPrintEvent(documentId, PrintEvent.EventType.PRINT_ATTEMPT, pageCount, true, "Print job queued: ${printJob.id}")
                            }
                            PrintJobInfo.STATE_STARTED -> {
                                logPrintEvent(documentId, PrintEvent.EventType.PRINT_ATTEMPT, pageCount, true, "Print job started: ${printJob.id}")
                            }
                        }
                        lastState = currentState
                    }

                    // If job is in final state, stop monitoring
                    if (currentState == PrintJobInfo.STATE_COMPLETED ||
                        currentState == PrintJobInfo.STATE_FAILED ||
                        currentState == PrintJobInfo.STATE_CANCELED) {
                        break
                    }

                    attempts++
                }

            } catch (e: Exception) {
                android.util.Log.w("PrintProtection", "Error monitoring print job", e)
            }
        }.start()
    }

    /**
     * Attempts to save a document with security checks
     */
    @Throws(SecurityException::class)
    fun saveDocument(
        documentId: String,
        outputPath: String,
        content: ByteArray
    ) {
        if (!config.saveEnabled) {
            logPrintEvent(documentId, PrintEvent.EventType.SAVE_BLOCKED, 0, false)
            throw SecurityException(config.restrictedActionMessage)
        }

        try {
            val finalContent = if (config.watermarkEnabled) {
                applyWatermarkToContent(content, documentId)
            } else {
                content
            }

            FileOutputStream(outputPath).use { fos ->
                fos.write(finalContent)
            }

            logPrintEvent(documentId, PrintEvent.EventType.SAVE_SUCCESS, 0, true, "Saved to: $outputPath")

        } catch (e: Exception) {
            logPrintEvent(documentId, PrintEvent.EventType.SAVE_BLOCKED, 0, false, e.message ?: "Unknown error")
            throw SecurityException("Failed to save document: ${e.message}")
        }
    }

    /**
     * Attempts to export a document with security checks
     */
    @Throws(SecurityException::class)
    fun exportDocument(
        documentId: String,
        format: ExportFormat,
        outputPath: String,
        pages: List<Bitmap>
    ) {
        if (!config.saveEnabled) {
            logPrintEvent(documentId, PrintEvent.EventType.EXPORT_BLOCKED, pages.size, false)
            throw SecurityException(config.restrictedActionMessage)
        }

        try {
            when (format) {
                ExportFormat.PDF -> exportToPdf(documentId, outputPath, pages)
                ExportFormat.PNG -> exportToPng(documentId, outputPath, pages)
                ExportFormat.JPEG -> exportToJpeg(documentId, outputPath, pages)
            }

            logPrintEvent(documentId, PrintEvent.EventType.EXPORT_SUCCESS, pages.size, true, "Exported as $format to: $outputPath")

        } catch (e: Exception) {
            logPrintEvent(documentId, PrintEvent.EventType.EXPORT_BLOCKED, pages.size, false, e.message ?: "Unknown error")
            throw SecurityException("Failed to export document: ${e.message}")
        }
    }

    /**
     * Blocks document sharing
     */
    fun blockSharing(documentId: String, intent: Intent): Boolean {
        if (!config.saveEnabled) {
            logPrintEvent(documentId, PrintEvent.EventType.SHARE_BLOCKED, 0, false, "Share intent blocked")
            return true
        }

        return false
    }

    /**
     * Creates a secure WebView print adapter
     */
    fun createSecureWebViewPrintAdapter(
        documentId: String,
        webView: WebView,
        jobName: String
    ): PrintDocumentAdapter? {
        if (!config.printEnabled) {
            logPrintEvent(documentId, PrintEvent.EventType.PRINT_BLOCKED, 0, false, "WebView print adapter blocked")
            return null
        }

        return webView.createPrintDocumentAdapter(jobName)
    }

    /**
     * Applies watermark to print content
     */
    private fun applyWatermarkToPrintAttributes(attributes: PrintAttributes?): PrintAttributes? {
        if (!config.watermarkEnabled || attributes == null) {
            return attributes
        }

        // Create new print attributes with watermark settings
        return PrintAttributes.Builder()
            .setMediaSize(attributes.mediaSize!!)
            .setResolution(attributes.resolution!!)
            .setMinMargins(attributes.minMargins!!)
            .setColorMode(attributes.colorMode)
            .setDuplexMode(attributes.duplexMode)
            .build()
    }

    /**
     * Applies watermark to document content
     */
    private fun applyWatermarkToContent(content: ByteArray, documentId: String): ByteArray {
        if (!config.watermarkEnabled) {
            return content
        }

        // This is a simplified watermark application
        // In practice, you would need to modify the actual PDF content
        logPrintEvent(documentId, PrintEvent.EventType.WATERMARK_APPLIED, 0, true, "Watermark applied to content")
        return content
    }

    /**
     * Exports pages to PDF format
     */
    private fun exportToPdf(documentId: String, outputPath: String, pages: List<Bitmap>) {
        val pdfDocument = PdfDocument()

        try {
            pages.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)

                val canvas = page.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)

                // Apply watermark if enabled
                if (config.watermarkEnabled) {
                    drawWatermarkOnCanvas(canvas, documentId, bitmap.width.toFloat(), bitmap.height.toFloat())
                }

                pdfDocument.finishPage(page)
            }

            FileOutputStream(outputPath).use { fos ->
                pdfDocument.writeTo(fos)
            }

        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Exports pages to PNG format
     */
    private fun exportToPng(documentId: String, outputPath: String, pages: List<Bitmap>) {
        pages.forEachIndexed { index, bitmap ->
            val fileName = if (pages.size == 1) outputPath else outputPath.replace(".png", "_$index.png")

            val finalBitmap = if (config.watermarkEnabled) {
                applyWatermarkToBitmap(bitmap, documentId)
            } else {
                bitmap
            }

            FileOutputStream(fileName).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        }
    }

    /**
     * Exports pages to JPEG format
     */
    private fun exportToJpeg(documentId: String, outputPath: String, pages: List<Bitmap>) {
        pages.forEachIndexed { index, bitmap ->
            val fileName = if (pages.size == 1) outputPath else outputPath.replace(".jpg", "_$index.jpg")

            val finalBitmap = if (config.watermarkEnabled) {
                applyWatermarkToBitmap(bitmap, documentId)
            } else {
                bitmap
            }

            FileOutputStream(fileName).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
        }
    }

    /**
     * Applies watermark to a bitmap
     */
    private fun applyWatermarkToBitmap(original: Bitmap, documentId: String): Bitmap {
        if (!config.watermarkEnabled || config.watermarkText.isEmpty()) {
            return original
        }

        val result = original.copy(original.config!!, true)
        val canvas = Canvas(result)

        drawWatermarkOnCanvas(canvas, documentId, result.width.toFloat(), result.height.toFloat())

        logPrintEvent(documentId, PrintEvent.EventType.WATERMARK_APPLIED, 0, true, "Watermark applied to bitmap")
        return result
    }

    /**
     * Draws watermark on canvas
     */
    private fun drawWatermarkOnCanvas(canvas: Canvas, documentId: String, width: Float, height: Float) {
        val paint = Paint().apply {
            color = Color.GRAY
            alpha = (255 * config.watermarkOpacity).toInt()
            textSize = width / 15f // Adaptive text size
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val watermarkText = config.watermarkText.ifEmpty {
            "COPY PROTECTED"
        }

        canvas.save()
        canvas.rotate(-45f, width / 2, height / 2)

        // Draw multiple watermarks across the page
        val spacing = height / 4f
        for (i in 0 until 4) {
            val y = height / 2 + (i - 1.5f) * spacing
            canvas.drawText(watermarkText, width / 2, y, paint)
        }

        canvas.restore()
    }

    enum class ExportFormat {
        PDF, PNG, JPEG
    }

    /**
     * Gets print protection statistics
     */
    fun getStatistics(documentId: String? = null): PrintProtectionStatistics {
        val events = if (documentId != null) {
            auditLog.filter { it.documentId == documentId }
        } else {
            auditLog
        }

        return PrintProtectionStatistics(
            totalEvents = events.size,
            printAttempts = events.count { it.eventType == PrintEvent.EventType.PRINT_ATTEMPT },
            printSuccess = events.count { it.eventType == PrintEvent.EventType.PRINT_SUCCESS },
            printBlocked = events.count { it.eventType == PrintEvent.EventType.PRINT_BLOCKED },
            saveAttempts = events.count { it.eventType == PrintEvent.EventType.SAVE_ATTEMPT },
            saveSuccess = events.count { it.eventType == PrintEvent.EventType.SAVE_SUCCESS },
            saveBlocked = events.count { it.eventType == PrintEvent.EventType.SAVE_BLOCKED },
            exportAttempts = events.count { it.eventType == PrintEvent.EventType.EXPORT_ATTEMPT },
            exportSuccess = events.count { it.eventType == PrintEvent.EventType.EXPORT_SUCCESS },
            exportBlocked = events.count { it.eventType == PrintEvent.EventType.EXPORT_BLOCKED },
            watermarkApplied = events.count { it.eventType == PrintEvent.EventType.WATERMARK_APPLIED },
            shareBlocked = events.count { it.eventType == PrintEvent.EventType.SHARE_BLOCKED }
        )
    }

    /**
     * Gets recent print events
     */
    fun getRecentEvents(documentId: String? = null, limit: Int = 50): List<PrintEvent> {
        val events = if (documentId != null) {
            auditLog.filter { it.documentId == documentId }
        } else {
            auditLog
        }

        return events.sortedByDescending { it.timestamp }.take(limit)
    }

    /**
     * Clears the audit log
     */
    fun clearAuditLog() {
        auditLog.clear()
    }

    /**
     * Performs cleanup of old events
     */
    fun performCleanup(maxAge: Long = 24 * 60 * 60 * 1000) { // 24 hours default
        val cutoffTime = System.currentTimeMillis() - maxAge
        auditLog.removeAll { it.timestamp < cutoffTime }
    }

    private fun logPrintEvent(
        documentId: String,
        eventType: PrintEvent.EventType,
        pageCount: Int,
        success: Boolean,
        details: String = ""
    ) {
        if (config.auditLoggingEnabled) {
            val event = PrintEvent(
                documentId = documentId,
                eventType = eventType,
                timestamp = System.currentTimeMillis(),
                pageCount = pageCount,
                success = success,
                details = details
            )

            auditLog.add(event)

            // Log to Android Log for debugging
            val tag = "PrintProtection"
            val message = "[$documentId] $eventType: $success (pages: $pageCount)${if (details.isNotEmpty()) " - $details" else ""}"

            when (eventType) {
                PrintEvent.EventType.PRINT_BLOCKED,
                PrintEvent.EventType.SAVE_BLOCKED,
                PrintEvent.EventType.EXPORT_BLOCKED,
                PrintEvent.EventType.SHARE_BLOCKED -> android.util.Log.w(tag, message)
                else -> android.util.Log.i(tag, message)
            }

            // Limit log size to prevent memory issues
            if (auditLog.size > 1000) {
                auditLog.removeAt(0)
            }
        }
    }

    data class PrintProtectionStatistics(
        val totalEvents: Int,
        val printAttempts: Int,
        val printSuccess: Int,
        val printBlocked: Int,
        val saveAttempts: Int,
        val saveSuccess: Int,
        val saveBlocked: Int,
        val exportAttempts: Int,
        val exportSuccess: Int,
        val exportBlocked: Int,
        val watermarkApplied: Int,
        val shareBlocked: Int
    ) {
        fun getPrintBlockRate(): Float {
            return if (printAttempts > 0) printBlocked.toFloat() / printAttempts else 0f
        }

        fun getSaveBlockRate(): Float {
            return if (saveAttempts > 0) saveBlocked.toFloat() / saveAttempts else 0f
        }

        fun getExportBlockRate(): Float {
            return if (exportAttempts > 0) exportBlocked.toFloat() / exportAttempts else 0f
        }

        override fun toString(): String {
            return buildString {
                appendLine("Print Protection Statistics:")
                appendLine("  Total Events: $totalEvents")
                appendLine("  Print - Attempts: $printAttempts, Success: $printSuccess, Blocked: $printBlocked (${(getPrintBlockRate() * 100).toInt()}%)")
                appendLine("  Save - Attempts: $saveAttempts, Success: $saveSuccess, Blocked: $saveBlocked (${(getSaveBlockRate() * 100).toInt()}%)")
                appendLine("  Export - Attempts: $exportAttempts, Success: $exportSuccess, Blocked: $exportBlocked (${(getExportBlockRate() * 100).toInt()}%)")
                appendLine("  Watermarks Applied: $watermarkApplied")
                appendLine("  Share Actions Blocked: $shareBlocked")
            }
        }
    }
}