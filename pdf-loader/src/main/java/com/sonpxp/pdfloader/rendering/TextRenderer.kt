package com.sonpxp.pdfloader.rendering


import android.graphics.*
import com.sonpxp.pdfloader.model.Configuration
import kotlin.math.max
import kotlin.math.min

/**
 * Handles text selection rendering and text-related visual features
 * Provides text highlighting, selection bounds, and text enhancement
 */
class TextRenderer(
    private val configuration: Configuration
) {

    data class TextSelection(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val pageNumber: Int,
        val selectedText: String,
        val bounds: List<RectF> = emptyList()
    )

    data class TextHighlight(
        val bounds: RectF,
        val color: Int,
        val alpha: Float = 0.3f,
        val borderColor: Int? = null,
        val borderWidth: Float = 0f
    )

    private val selectionPaint = Paint().apply {
        color = Color.BLUE
        alpha = 80
        style = Paint.Style.FILL
    }

    private val highlightPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textBorderPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeWidth = 2f
    }

    private val handlePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Selection state
    private var currentSelection: TextSelection? = null
    private var selectionHandleSize = 24f
    private var selectionColor = Color.BLUE
    private var selectionAlpha = 80

    /**
     * Draws text selection on canvas
     */
    fun drawTextSelection(canvas: Canvas, selection: TextSelection, zoomLevel: Float = 1f) {
        if (!configuration.textSelectionEnabled) return

        selectionPaint.color = selectionColor
        selectionPaint.alpha = selectionAlpha

        // Draw selection bounds
        for (bound in selection.bounds) {
            val scaledBound = RectF(
                bound.left * zoomLevel,
                bound.top * zoomLevel,
                bound.right * zoomLevel,
                bound.bottom * zoomLevel
            )
            canvas.drawRect(scaledBound, selectionPaint)
        }

        // Draw selection handles
        if (selection.bounds.isNotEmpty()) {
            drawSelectionHandles(canvas, selection, zoomLevel)
        }
    }

    /**
     * Draws text highlights on canvas
     */
    fun drawTextHighlights(canvas: Canvas, highlights: List<TextHighlight>, zoomLevel: Float = 1f) {
        for (highlight in highlights) {
            drawTextHighlight(canvas, highlight, zoomLevel)
        }
    }

    /**
     * Draws a single text highlight
     */
    fun drawTextHighlight(canvas: Canvas, highlight: TextHighlight, zoomLevel: Float = 1f) {
        highlightPaint.color = highlight.color
        highlightPaint.alpha = (highlight.alpha * 255).toInt()

        val scaledBounds = RectF(
            highlight.bounds.left * zoomLevel,
            highlight.bounds.top * zoomLevel,
            highlight.bounds.right * zoomLevel,
            highlight.bounds.bottom * zoomLevel
        )

        // Draw highlight background
        canvas.drawRect(scaledBounds, highlightPaint)

        // Draw border if specified
        highlight.borderColor?.let { borderColor ->
            textBorderPaint.color = borderColor
            textBorderPaint.strokeWidth = highlight.borderWidth * zoomLevel
            canvas.drawRect(scaledBounds, textBorderPaint)
        }
    }

    /**
     * Draws selection handles
     */
    private fun drawSelectionHandles(canvas: Canvas, selection: TextSelection, zoomLevel: Float) {
        if (selection.bounds.isEmpty()) return

        val handleRadius = selectionHandleSize * zoomLevel / 2f

        // Start handle (top-left of first bound)
        val firstBound = selection.bounds.first()
        val startHandleX = firstBound.left * zoomLevel
        val startHandleY = firstBound.top * zoomLevel

        canvas.drawCircle(startHandleX, startHandleY, handleRadius, handlePaint)

        // End handle (bottom-right of last bound)
        val lastBound = selection.bounds.last()
        val endHandleX = lastBound.right * zoomLevel
        val endHandleY = lastBound.bottom * zoomLevel

        canvas.drawCircle(endHandleX, endHandleY, handleRadius, handlePaint)
    }

    /**
     * Creates text selection from coordinates
     */
    fun createTextSelection(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        pageNumber: Int,
        selectedText: String = ""
    ): TextSelection {
        // Normalize coordinates
        val normalizedStartX = min(startX, endX)
        val normalizedStartY = min(startY, endY)
        val normalizedEndX = max(startX, endX)
        val normalizedEndY = max(startY, endY)

        // Create selection bounds
        val bounds = createSelectionBounds(
            normalizedStartX,
            normalizedStartY,
            normalizedEndX,
            normalizedEndY
        )

        return TextSelection(
            startX = normalizedStartX,
            startY = normalizedStartY,
            endX = normalizedEndX,
            endY = normalizedEndY,
            pageNumber = pageNumber,
            selectedText = selectedText,
            bounds = bounds
        )
    }

    /**
     * Creates selection bounds rectangles
     */
    private fun createSelectionBounds(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): List<RectF> {
        val bounds = mutableListOf<RectF>()

        // Simple single-line selection for now
        // In a real implementation, this would handle multi-line text
        val lineHeight = 20f // This should come from text metrics

        if (kotlin.math.abs(endY - startY) <= lineHeight) {
            // Single line selection
            bounds.add(RectF(startX, startY, endX, startY + lineHeight))
        } else {
            // Multi-line selection
            val numLines = ((endY - startY) / lineHeight).toInt() + 1

            for (i in 0 until numLines) {
                val lineY = startY + i * lineHeight
                val lineStartX = if (i == 0) startX else 0f
                val lineEndX = if (i == numLines - 1) endX else 1000f // Page width

                bounds.add(RectF(lineStartX, lineY, lineEndX, lineY + lineHeight))
            }
        }

        return bounds
    }

    /**
     * Checks if point is within selection handle
     */
    fun isPointInSelectionHandle(
        x: Float,
        y: Float,
        selection: TextSelection,
        zoomLevel: Float = 1f
    ): SelectionHandle? {
        if (selection.bounds.isEmpty()) return null

        val handleRadius = selectionHandleSize * zoomLevel / 2f

        // Check start handle
        val firstBound = selection.bounds.first()
        val startHandleX = firstBound.left * zoomLevel
        val startHandleY = firstBound.top * zoomLevel

        val startDistance = kotlin.math.sqrt(
            (x - startHandleX) * (x - startHandleX) + (y - startHandleY) * (y - startHandleY)
        )

        if (startDistance <= handleRadius * 1.5f) {
            return SelectionHandle.START
        }

        // Check end handle
        val lastBound = selection.bounds.last()
        val endHandleX = lastBound.right * zoomLevel
        val endHandleY = lastBound.bottom * zoomLevel

        val endDistance = kotlin.math.sqrt(
            (x - endHandleX) * (x - endHandleX) + (y - endHandleY) * (y - endHandleY)
        )

        if (endDistance <= handleRadius * 1.5f) {
            return SelectionHandle.END
        }

        return null
    }

    /**
     * Updates selection by moving a handle
     */
    fun updateSelectionHandle(
        selection: TextSelection,
        handle: SelectionHandle,
        newX: Float,
        newY: Float
    ): TextSelection {
        return when (handle) {
            SelectionHandle.START -> createTextSelection(
                newX, newY,
                selection.endX, selection.endY,
                selection.pageNumber,
                selection.selectedText
            )
            SelectionHandle.END -> createTextSelection(
                selection.startX, selection.startY,
                newX, newY,
                selection.pageNumber,
                selection.selectedText
            )
        }
    }

    /**
     * Creates text highlight from selection
     */
    fun createHighlightFromSelection(
        selection: TextSelection,
        color: Int = Color.YELLOW,
        alpha: Float = 0.3f
    ): List<TextHighlight> {
        return selection.bounds.map { bound ->
            TextHighlight(
                bounds = bound,
                color = color,
                alpha = alpha
            )
        }
    }

    /**
     * Gets selection bounds that encompass all selection rectangles
     */
    fun getSelectionBounds(selection: TextSelection): RectF? {
        if (selection.bounds.isEmpty()) return null

        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = Float.MIN_VALUE
        var bottom = Float.MIN_VALUE

        for (bound in selection.bounds) {
            left = min(left, bound.left)
            top = min(top, bound.top)
            right = max(right, bound.right)
            bottom = max(bottom, bound.bottom)
        }

        return RectF(left, top, right, bottom)
    }

    /**
     * Sets current selection
     */
    fun setCurrentSelection(selection: TextSelection?) {
        currentSelection = selection
    }

    /**
     * Gets current selection
     */
    fun getCurrentSelection(): TextSelection? = currentSelection

    /**
     * Clears current selection
     */
    fun clearSelection() {
        currentSelection = null
    }

    /**
     * Sets selection appearance
     */
    fun setSelectionAppearance(color: Int, alpha: Int, handleSize: Float) {
        selectionColor = color
        selectionAlpha = alpha
        selectionHandleSize = handleSize

        selectionPaint.color = color
        selectionPaint.alpha = alpha
        handlePaint.color = color
    }

    /**
     * Checks if text selection is enabled
     */
    fun isTextSelectionEnabled(): Boolean = configuration.textSelectionEnabled

    /**
     * Creates common highlight colors
     */
    fun createHighlightColor(type: HighlightType): Int {
        return when (type) {
            HighlightType.YELLOW -> Color.YELLOW
            HighlightType.GREEN -> Color.GREEN
            HighlightType.BLUE -> Color.CYAN
            HighlightType.PINK -> Color.MAGENTA
            HighlightType.ORANGE -> Color.rgb(255, 165, 0)
            HighlightType.PURPLE -> Color.rgb(128, 0, 128)
        }
    }

    enum class SelectionHandle {
        START, END
    }

    enum class HighlightType {
        YELLOW, GREEN, BLUE, PINK, ORANGE, PURPLE
    }

    /**
     * Gets text renderer statistics
     */
    fun getStatistics(): TextRendererStatistics {
        return TextRendererStatistics(
            textSelectionEnabled = configuration.textSelectionEnabled,
            hasCurrentSelection = currentSelection != null,
            selectionColor = selectionColor,
            selectionAlpha = selectionAlpha,
            handleSize = selectionHandleSize,
            currentSelectionBounds = currentSelection?.bounds?.size ?: 0
        )
    }

    data class TextRendererStatistics(
        val textSelectionEnabled: Boolean,
        val hasCurrentSelection: Boolean,
        val selectionColor: Int,
        val selectionAlpha: Int,
        val handleSize: Float,
        val currentSelectionBounds: Int
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Text Renderer Statistics:")
                appendLine("  Text Selection Enabled: $textSelectionEnabled")
                appendLine("  Has Current Selection: $hasCurrentSelection")
                appendLine("  Selection Color: #${Integer.toHexString(selectionColor).uppercase()}")
                appendLine("  Selection Alpha: $selectionAlpha")
                appendLine("  Handle Size: ${handleSize}px")
                appendLine("  Current Selection Bounds: $currentSelectionBounds")
            }
        }
    }
}