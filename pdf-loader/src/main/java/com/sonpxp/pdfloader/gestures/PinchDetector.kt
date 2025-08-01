package com.sonpxp.pdfloader.gestures

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.sonpxp.pdfloader.model.Configuration
import com.sonpxp.pdfloader.utils.MathUtils
import kotlin.math.*

/**
 * Detects pinch zoom gestures
 * Calculates scale factor, focus point, and handles multi-touch events
 */
class PinchDetector(
    private val context: Context,
    private val configuration: Configuration
) {

    interface PinchCallback {
        fun onPinchStart(detector: PinchDetector): Boolean
        fun onPinch(detector: PinchDetector): Boolean
        fun onPinchEnd(detector: PinchDetector)
    }

    private var callback: PinchCallback? = null

    // Configuration
    private val minSpanSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private val minScaleFactor = 0.1f
    private val maxScaleFactor = 10f

    // State tracking
    private var isInProgress = false
    private var initialSpan = 0f
    private var currentSpan = 0f
    private var previousSpan = 0f
    private var scaleFactor = 1f
    private var focusX = 0f
    private var focusY = 0f
    private var previousFocusX = 0f
    private var previousFocusY = 0f

    // Touch point tracking
    private var activePointerCount = 0
    private val activePointers = mutableMapOf<Int, PointerInfo>()

    data class PointerInfo(
        var x: Float,
        var y: Float,
        var id: Int
    )

    fun setCallbacks(callback: PinchCallback) {
        this.callback = callback
    }

    /**
     * Processes touch events for pinch detection
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                return handleActionDown(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                return handlePointerDown(event)
            }
            MotionEvent.ACTION_MOVE -> {
                return handleActionMove(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                return handlePointerUp(event)
            }
            MotionEvent.ACTION_UP -> {
                return handleActionUp(event)
            }
            MotionEvent.ACTION_CANCEL -> {
                return handleActionCancel()
            }
        }
        return false
    }

    private fun handleActionDown(event: MotionEvent): Boolean {
        // First pointer down - not a pinch yet
        activePointerCount = 1
        activePointers.clear()
        activePointers[event.getPointerId(0)] = PointerInfo(
            x = event.x,
            y = event.y,
            id = event.getPointerId(0)
        )

        return false
    }

    private fun handlePointerDown(event: MotionEvent): Boolean {
        if (!configuration.pinchZoomEnabled) return false

        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        // Add new pointer
        activePointers[pointerId] = PointerInfo(
            x = event.getX(actionIndex),
            y = event.getY(actionIndex),
            id = pointerId
        )

        activePointerCount = event.pointerCount

        // Start pinch detection if we have 2 or more pointers
        if (activePointerCount >= 2 && !isInProgress) {
            startPinch(event)
        }

        return isInProgress
    }

    private fun handleActionMove(event: MotionEvent): Boolean {
        if (!isInProgress || activePointerCount < 2) return false

        // Update pointer positions
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            activePointers[pointerId]?.let { pointer ->
                pointer.x = event.getX(i)
                pointer.y = event.getY(i)
            }
        }

        // Calculate new span and focus
        calculateSpanAndFocus()

        // Check if span changed significantly
        val spanDelta = abs(currentSpan - previousSpan)
        if (spanDelta > minSpanSlop) {
            updateScaleFactor()

            val handled = callback?.onPinch(this) ?: false

            // Update previous values
            previousSpan = currentSpan
            previousFocusX = focusX
            previousFocusY = focusY

            return handled
        }

        return true
    }

    private fun handlePointerUp(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        // Remove pointer
        activePointers.remove(pointerId)
        activePointerCount = event.pointerCount - 1

        if (isInProgress) {
            if (activePointerCount < 2) {
                // End pinch if less than 2 pointers remain
                endPinch()
            } else {
                // Recalculate with remaining pointers
                calculateSpanAndFocus()
                previousSpan = currentSpan
                previousFocusX = focusX
                previousFocusY = focusY
            }
        }

        return isInProgress
    }

    private fun handleActionUp(event: MotionEvent): Boolean {
        if (isInProgress) {
            endPinch()
        }

        activePointers.clear()
        activePointerCount = 0

        return false
    }

    private fun handleActionCancel(): Boolean {
        if (isInProgress) {
            endPinch()
        }

        reset()
        return true
    }

    private fun startPinch(event: MotionEvent) {
        if (isInProgress) return

        calculateSpanAndFocus()

        if (currentSpan > minSpanSlop) {
            isInProgress = true
            initialSpan = currentSpan
            previousSpan = currentSpan
            previousFocusX = focusX
            previousFocusY = focusY
            scaleFactor = 1f

            callback?.onPinchStart(this)
        }
    }

    private fun endPinch() {
        if (!isInProgress) return

        callback?.onPinchEnd(this)
        reset()
    }

    private fun reset() {
        isInProgress = false
        initialSpan = 0f
        currentSpan = 0f
        previousSpan = 0f
        scaleFactor = 1f
        focusX = 0f
        focusY = 0f
        previousFocusX = 0f
        previousFocusY = 0f
    }

    private fun calculateSpanAndFocus() {
        if (activePointers.size < 2) {
            currentSpan = 0f
            focusX = 0f
            focusY = 0f
            return
        }

        // Use first two active pointers for span calculation
        val pointers = activePointers.values.take(2)
        val p1 = pointers[0]
        val p2 = pointers[1]

        // Calculate span (distance between two points)
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        currentSpan = sqrt(dx * dx + dy * dy)

        // Calculate focus point (center between two points)
        focusX = (p1.x + p2.x) / 2f
        focusY = (p1.y + p2.y) / 2f
    }

    private fun updateScaleFactor() {
        if (previousSpan > 0 && currentSpan > 0) {
            val newScaleFactor = currentSpan / previousSpan
            scaleFactor = MathUtils.clamp(newScaleFactor, minScaleFactor, maxScaleFactor)
        }
    }

    /**
     * Gets the current scale factor since last event
     */
    fun getScaleFactor(): Float = scaleFactor

    /**
     * Gets the total scale factor since pinch started
     */
    fun getTotalScaleFactor(): Float {
        return if (initialSpan > 0) currentSpan / initialSpan else 1f
    }

    /**
     * Gets the current focus X coordinate
     */
    fun getFocusX(): Float = focusX

    /**
     * Gets the current focus Y coordinate
     */
    fun getFocusY(): Float = focusY

    /**
     * Gets the focus movement since last event
     */
    fun getFocusDeltaX(): Float = focusX - previousFocusX

    fun getFocusDeltaY(): Float = focusY - previousFocusY

    /**
     * Gets the current span (distance between pointers)
     */
    fun getCurrentSpan(): Float = currentSpan

    /**
     * Gets the initial span when pinch started
     */
    fun getInitialSpan(): Float = initialSpan

    /**
     * Checks if pinch gesture is in progress
     */
    fun isInProgress(): Boolean = isInProgress

    /**
     * Gets the number of active pointers
     */
    fun getActivePointerCount(): Int = activePointerCount

    /**
     * Cancels current pinch gesture
     */
    fun cancel() {
        if (isInProgress) {
            endPinch()
        }
        reset()
        activePointers.clear()
        activePointerCount = 0
    }

    /**
     * Gets the velocity of the pinch gesture
     */
    fun getPinchVelocity(): Float {
        // Simple velocity calculation based on span change
        return if (previousSpan > 0) {
            (currentSpan - previousSpan) / 16f // Assuming 60fps, so ~16ms per frame
        } else {
            0f
        }
    }

    /**
     * Gets the rotation angle between the two pointers
     */
    fun getRotationAngle(): Float {
        if (activePointers.size < 2) return 0f

        val pointers = activePointers.values.take(2)
        val p1 = pointers[0]
        val p2 = pointers[1]

        val dx = p2.x - p1.x
        val dy = p2.y - p1.y

        return atan2(dy, dx) * 180f / PI.toFloat()
    }

    /**
     * Checks if the pinch gesture is expanding (zooming in)
     */
    fun isExpanding(): Boolean = scaleFactor > 1f

    /**
     * Checks if the pinch gesture is contracting (zooming out)
     */
    fun isContracting(): Boolean = scaleFactor < 1f

    /**
     * Gets pinch detector statistics
     */
    fun getStatistics(): PinchStatistics {
        return PinchStatistics(
            isInProgress = isInProgress,
            activePointerCount = activePointerCount,
            currentSpan = currentSpan,
            initialSpan = initialSpan,
            scaleFactor = scaleFactor,
            totalScaleFactor = getTotalScaleFactor(),
            focusX = focusX,
            focusY = focusY,
            focusDeltaX = getFocusDeltaX(),
            focusDeltaY = getFocusDeltaY(),
            pinchVelocity = getPinchVelocity(),
            rotationAngle = getRotationAngle(),
            isExpanding = isExpanding(),
            isContracting = isContracting()
        )
    }

    data class PinchStatistics(
        val isInProgress: Boolean,
        val activePointerCount: Int,
        val currentSpan: Float,
        val initialSpan: Float,
        val scaleFactor: Float,
        val totalScaleFactor: Float,
        val focusX: Float,
        val focusY: Float,
        val focusDeltaX: Float,
        val focusDeltaY: Float,
        val pinchVelocity: Float,
        val rotationAngle: Float,
        val isExpanding: Boolean,
        val isContracting: Boolean
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Pinch Detector Statistics:")
                appendLine("  In Progress: $isInProgress")
                appendLine("  Active Pointers: $activePointerCount")
                appendLine("  Current Span: ${currentSpan.toInt()}px")
                appendLine("  Initial Span: ${initialSpan.toInt()}px")
                appendLine("  Scale Factor: ${String.format("%.3f", scaleFactor)}")
                appendLine("  Total Scale: ${String.format("%.3f", totalScaleFactor)}")
                appendLine("  Focus: (${focusX.toInt()}, ${focusY.toInt()})")
                appendLine("  Focus Delta: (${focusDeltaX.toInt()}, ${focusDeltaY.toInt()})")
                appendLine("  Pinch Velocity: ${String.format("%.2f", pinchVelocity)} px/frame")
                appendLine("  Rotation: ${String.format("%.1f", rotationAngle)}°")
                appendLine("  Direction: ${when {
                    isExpanding -> "Expanding (Zoom In)"
                    isContracting -> "Contracting (Zoom Out)"
                    else -> "Static"
                }}")
            }
        }
    }
}