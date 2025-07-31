package com.sonpxp.pdfloader.security


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Selection
import android.text.Spannable
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View

/**
 * Manages copy protection features for PDF documents
 * Controls text selection, clipboard access, and content copying
 */
class CopyProtection(
    private val context: Context,
    private val config: SecurityConfig
) {

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val auditLog = mutableListOf<CopyEvent>()

    /**
     * Represents a copy-related security event
     */
    data class CopyEvent(
        val documentId: String,
        val eventType: EventType,
        val timestamp: Long,
        val textLength: Int = 0,
        val success: Boolean,
        val details: String = ""
    ) {
        enum class EventType {
            TEXT_SELECTION_ATTEMPT,
            TEXT_SELECTION_BLOCKED,
            COPY_ATTEMPT,
            COPY_BLOCKED,
            COPY_SUCCESS,
            CLIPBOARD_ACCESS_BLOCKED,
            WATERMARK_APPLIED
        }
    }

    /**
     * Custom ActionMode.Callback to control text selection menu
     */
    inner class CopyProtectionCallback(
        private val documentId: String,
        private val originalCallback: ActionMode.Callback?
    ) : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (!config.textSelectionEnabled) {
                logCopyEvent(documentId, CopyEvent.EventType.TEXT_SELECTION_BLOCKED, 0, false)
                return false
            }

            // Let original callback create the menu first
            val result = originalCallback?.onCreateActionMode(mode, menu) ?: false

            // Remove copy-related menu items if copy is disabled
            if (!config.copyEnabled && menu != null) {
                removeCopyMenuItems(menu)
            }

            return result
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (!config.copyEnabled && menu != null) {
                removeCopyMenuItems(menu)
            }

            return originalCallback?.onPrepareActionMode(mode, menu) ?: false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item?.itemId) {
                android.R.id.copy -> {
                    if (!config.copyEnabled) {
                        logCopyEvent(documentId, CopyEvent.EventType.COPY_BLOCKED, 0, false, "Copy menu item blocked")
                        showSecurityMessage()
                        return true
                    }

                    // Allow copy but log it
                    val selectedText = getSelectedText()
                    logCopyEvent(documentId, CopyEvent.EventType.COPY_SUCCESS, selectedText.length, true)

                    // Apply watermark to copied text if enabled
                    if (config.watermarkEnabled) {
                        applyWatermarkToClipboard(selectedText, documentId)
                        return true
                    }
                }

                android.R.id.selectAll -> {
                    if (!config.textSelectionEnabled) {
                        logCopyEvent(documentId, CopyEvent.EventType.TEXT_SELECTION_BLOCKED, 0, false, "Select all blocked")
                        showSecurityMessage()
                        return true
                    }
                }
            }

            return originalCallback?.onActionItemClicked(mode, item) ?: false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            originalCallback?.onDestroyActionMode(mode)
        }

        private fun removeCopyMenuItems(menu: Menu) {
            menu.removeItem(android.R.id.copy)
            menu.removeItem(android.R.id.cut)
            menu.removeItem(android.R.id.shareText)
        }

        private fun getSelectedText(): String {
            // This would need to be implemented based on your text view implementation
            return ""
        }
    }

    /**
     * Checks if text selection is allowed for a document
     */
    fun isTextSelectionAllowed(documentId: String): Boolean {
        if (!config.textSelectionEnabled) {
            logCopyEvent(documentId, CopyEvent.EventType.TEXT_SELECTION_ATTEMPT, 0, false)
            return false
        }

        logCopyEvent(documentId, CopyEvent.EventType.TEXT_SELECTION_ATTEMPT, 0, true)
        return true
    }

    /**
     * Checks if copying is allowed for a document
     */
    fun isCopyAllowed(documentId: String, textLength: Int = 0): Boolean {
        if (!config.copyEnabled) {
            logCopyEvent(documentId, CopyEvent.EventType.COPY_ATTEMPT, textLength, false)
            return false
        }

        logCopyEvent(documentId, CopyEvent.EventType.COPY_ATTEMPT, textLength, true)
        return true
    }

    /**
     * Attempts to copy text to clipboard with security checks
     */
    @Throws(SecurityException::class)
    fun copyToClipboard(documentId: String, text: String, label: String = "PDF Text") {
        if (!config.copyEnabled) {
            logCopyEvent(documentId, CopyEvent.EventType.COPY_BLOCKED, text.length, false)
            throw SecurityException(config.restrictedActionMessage)
        }

        try {
            val finalText = if (config.watermarkEnabled) {
                applyTextWatermark(text, documentId)
            } else {
                text
            }

            val clipData = ClipData.newPlainText(label, finalText)
            clipboardManager.setPrimaryClip(clipData)

            logCopyEvent(documentId, CopyEvent.EventType.COPY_SUCCESS, text.length, true)

        } catch (e: Exception) {
            logCopyEvent(documentId, CopyEvent.EventType.COPY_BLOCKED, text.length, false, e.message ?: "Unknown error")
            throw SecurityException("Failed to copy text: ${e.message}")
        }
    }

    /**
     * Blocks clipboard access by clearing it
     */
    fun blockClipboardAccess(documentId: String) {
        try {
            val emptyClip = ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(emptyClip)
            logCopyEvent(documentId, CopyEvent.EventType.CLIPBOARD_ACCESS_BLOCKED, 0, true)
        } catch (e: Exception) {
            android.util.Log.w("CopyProtection", "Failed to clear clipboard", e)
        }
    }

    /**
     * Applies watermark to text before copying
     */
    private fun applyTextWatermark(text: String, documentId: String): String {
        if (!config.watermarkEnabled || config.watermarkText.isEmpty()) {
            return text
        }

        val watermark = "\n\n[${config.watermarkText}] - Document ID: $documentId - Copied at: ${System.currentTimeMillis()}"
        logCopyEvent(documentId, CopyEvent.EventType.WATERMARK_APPLIED, text.length, true)

        return text + watermark
    }

    /**
     * Applies watermark directly to clipboard
     */
    private fun applyWatermarkToClipboard(text: String, documentId: String) {
        val watermarkedText = applyTextWatermark(text, documentId)
        val clipData = ClipData.newPlainText("PDF Text", watermarkedText)
        clipboardManager.setPrimaryClip(clipData)
    }

    /**
     * Creates a custom ActionMode.Callback with copy protection
     */
    fun createProtectedActionModeCallback(
        documentId: String,
        originalCallback: ActionMode.Callback?
    ): ActionMode.Callback {
        return CopyProtectionCallback(documentId, originalCallback)
    }

    /**
     * Disables text selection on a view
     */
    fun disableTextSelection(view: View) {
        if (!config.textSelectionEnabled) {
            view.isLongClickable = false
            view.isFocusable = false
            view.isFocusableInTouchMode = false
        }
    }

    /**
     * Applies copy protection to a spannable text
     */
    fun protectSpannableText(text: Spannable, documentId: String) {
        if (!config.textSelectionEnabled) {
            // Clear any existing selection
            Selection.removeSelection(text)

            // Make text non-selectable by removing selection spans
            val selectionSpans = text.getSpans(0, text.length, Any::class.java)
            for (span in selectionSpans) {
                if (span.javaClass.name.contains("Selection")) {
                    text.removeSpan(span)
                }
            }

            logCopyEvent(documentId, CopyEvent.EventType.TEXT_SELECTION_BLOCKED, text.length, false)
        }
    }

    /**
     * Draws a watermark overlay on canvas
     */
    fun drawWatermarkOverlay(canvas: Canvas, documentId: String, pageWidth: Float, pageHeight: Float) {
        if (!config.watermarkEnabled || config.watermarkText.isEmpty()) {
            return
        }

        val paint = Paint().apply {
            color = android.graphics.Color.GRAY
            alpha = (255 * config.watermarkOpacity).toInt()
            textSize = 48f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Calculate text bounds
        val bounds = Rect()
        paint.getTextBounds(config.watermarkText, 0, config.watermarkText.length, bounds)

        // Draw watermark diagonally across the page
        canvas.save()
        canvas.rotate(-45f, pageWidth / 2, pageHeight / 2)

        val spacing = 200f
        val startY = -bounds.height().toFloat()
        val endY = pageHeight + bounds.height()

        var y = startY
        while (y < endY) {
            canvas.drawText(config.watermarkText, pageWidth / 2, y, paint)
            y += spacing
        }

        canvas.restore()

        logCopyEvent(documentId, CopyEvent.EventType.WATERMARK_APPLIED, 0, true, "Overlay watermark applied")
    }

    /**
     * Shows security message to user when action is blocked
     */
    private fun showSecurityMessage() {
        // This could be implemented as a Toast, Snackbar, or Dialog
        android.widget.Toast.makeText(
            context,
            config.restrictedActionMessage,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Monitors clipboard for unauthorized access
     */
    fun startClipboardMonitoring(documentId: String) {
        if (config.copyEnabled) return

        clipboardManager.addPrimaryClipChangedListener {
            // If copy is disabled but clipboard changed, it might be unauthorized
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text?.toString() ?: ""
                if (clipText.isNotEmpty()) {
                    logCopyEvent(documentId, CopyEvent.EventType.COPY_BLOCKED, clipText.length, false, "Unauthorized clipboard access detected")

                    // Clear clipboard to prevent data leakage
                    blockClipboardAccess(documentId)
                }
            }
        }
    }

    /**
     * Gets copy protection statistics
     */
    fun getStatistics(documentId: String? = null): CopyProtectionStatistics {
        val events = if (documentId != null) {
            auditLog.filter { it.documentId == documentId }
        } else {
            auditLog
        }

        return CopyProtectionStatistics(
            totalEvents = events.size,
            selectionAttempts = events.count { it.eventType == CopyEvent.EventType.TEXT_SELECTION_ATTEMPT },
            selectionBlocked = events.count { it.eventType == CopyEvent.EventType.TEXT_SELECTION_BLOCKED },
            copyAttempts = events.count { it.eventType == CopyEvent.EventType.COPY_ATTEMPT },
            copySuccess = events.count { it.eventType == CopyEvent.EventType.COPY_SUCCESS },
            copyBlocked = events.count { it.eventType == CopyEvent.EventType.COPY_BLOCKED },
            watermarkApplied = events.count { it.eventType == CopyEvent.EventType.WATERMARK_APPLIED },
            clipboardBlocked = events.count { it.eventType == CopyEvent.EventType.CLIPBOARD_ACCESS_BLOCKED }
        )
    }

    /**
     * Gets recent copy events
     */
    fun getRecentEvents(documentId: String? = null, limit: Int = 50): List<CopyEvent> {
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

    /**
     * Checks if a specific action is allowed
     */
    fun isActionAllowed(action: ProtectedAction, documentId: String): Boolean {
        return when (action) {
            ProtectedAction.TEXT_SELECTION -> {
                if (config.textSelectionEnabled) {
                    logCopyEvent(documentId, CopyEvent.EventType.TEXT_SELECTION_ATTEMPT, 0, true)
                    true
                } else {
                    logCopyEvent(documentId, CopyEvent.EventType.TEXT_SELECTION_BLOCKED, 0, false)
                    false
                }
            }
            ProtectedAction.COPY -> {
                if (config.copyEnabled) {
                    logCopyEvent(documentId, CopyEvent.EventType.COPY_ATTEMPT, 0, true)
                    true
                } else {
                    logCopyEvent(documentId, CopyEvent.EventType.COPY_BLOCKED, 0, false)
                    false
                }
            }
        }
    }

    enum class ProtectedAction {
        TEXT_SELECTION,
        COPY
    }

    private fun logCopyEvent(
        documentId: String,
        eventType: CopyEvent.EventType,
        textLength: Int,
        success: Boolean,
        details: String = ""
    ) {
        if (config.auditLoggingEnabled) {
            val event = CopyEvent(
                documentId = documentId,
                eventType = eventType,
                timestamp = System.currentTimeMillis(),
                textLength = textLength,
                success = success,
                details = details
            )

            auditLog.add(event)

            // Log to Android Log for debugging
            val tag = "CopyProtection"
            val message = "[$documentId] $eventType: $success (length: $textLength)${if (details.isNotEmpty()) " - $details" else ""}"

            when (eventType) {
                CopyEvent.EventType.TEXT_SELECTION_BLOCKED,
                CopyEvent.EventType.COPY_BLOCKED,
                CopyEvent.EventType.CLIPBOARD_ACCESS_BLOCKED -> android.util.Log.w(tag, message)
                else -> android.util.Log.i(tag, message)
            }

            // Limit log size to prevent memory issues
            if (auditLog.size > 1000) {
                auditLog.removeAt(0)
            }
        }
    }

    data class CopyProtectionStatistics(
        val totalEvents: Int,
        val selectionAttempts: Int,
        val selectionBlocked: Int,
        val copyAttempts: Int,
        val copySuccess: Int,
        val copyBlocked: Int,
        val watermarkApplied: Int,
        val clipboardBlocked: Int
    ) {
        fun getSelectionBlockRate(): Float {
            return if (selectionAttempts > 0) selectionBlocked.toFloat() / selectionAttempts else 0f
        }

        fun getCopyBlockRate(): Float {
            return if (copyAttempts > 0) copyBlocked.toFloat() / copyAttempts else 0f
        }

        fun getWatermarkRate(): Float {
            return if (copySuccess > 0) watermarkApplied.toFloat() / copySuccess else 0f
        }

        override fun toString(): String {
            return buildString {
                appendLine("Copy Protection Statistics:")
                appendLine("  Total Events: $totalEvents")
                appendLine("  Selection - Attempts: $selectionAttempts, Blocked: $selectionBlocked (${(getSelectionBlockRate() * 100).toInt()}%)")
                appendLine("  Copy - Attempts: $copyAttempts, Success: $copySuccess, Blocked: $copyBlocked (${(getCopyBlockRate() * 100).toInt()}%)")
                appendLine("  Watermarks Applied: $watermarkApplied (${(getWatermarkRate() * 100).toInt()}% of successful copies)")
                appendLine("  Clipboard Access Blocked: $clipboardBlocked")
            }
        }
    }
}