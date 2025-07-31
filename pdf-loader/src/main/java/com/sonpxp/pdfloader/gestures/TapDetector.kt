package com.sonpxp.pdfloader.gestures


import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.sonpxp.pdfloader.model.Configuration
import kotlin.math.abs

/**
 * Detects tap gestures (single tap, double tap, long press)
 * Handles timing and distance thresholds for accurate gesture recognition
 */
class TapDetector(
    private val context: Context,
    private val configuration: Configuration
) {

    interface TapCallback {
        fun onSingleTap(event: MotionEvent): Boolean
        fun onDoubleTap(event: MotionEvent): Boolean
        fun onLongPress(event: MotionEvent): Boolean
    }

    private val handler = Handler(Looper.getMainLooper())
    private var callback: TapCallback? = null

    // Configuration values
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong()

    // State tracking
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var tapCount = 0
    private var isLongPressTriggered = false
    private var isTapInProgress = false

    // Pending actions
    private var pendingSingleTapAction: Runnable? = null
    private var pendingLongPressAction: Runnable? = null

    fun setCallbacks(callback: TapCallback) {
        this.callback = callback
    }

    /**
     * Processes touch events for tap detection
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                return handleActionDown(event)
            }
            MotionEvent.ACTION_MOVE -> {
                return handleActionMove(event)
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
        // Cancel any pending actions
        cancelPendingActions()

        // Record down position and time
        downTime = event.eventTime
        downX = event.x
        downY = event.y
        isTapInProgress = true
        isLongPressTriggered = false

        // Schedule long press detection
        if (configuration.swipeEnabled) { // Only if interactions are enabled
            scheduleLongPress(event)
        }

        return true
    }

    private fun handleActionMove(event: MotionEvent): Boolean {
        if (!isTapInProgress) return false

        // Check if moved too far (outside touch slop)
        val deltaX = abs(event.x - downX)
        val deltaY = abs(event.y - downY)

        if (deltaX > touchSlop || deltaY > touchSlop) {
            // Movement too large, cancel tap
            cancelTap()
            return false
        }

        return true
    }

    private fun handleActionUp(event: MotionEvent): Boolean {
        if (!isTapInProgress) return false

        val upTime = event.eventTime
        val duration = upTime - downTime

        // Cancel long press since we got ACTION_UP
        cancelLongPress()

        // Check if it's a valid tap (not too long, not too short)
        if (duration > tapTimeout && !isLongPressTriggered) {
            // Too long for a tap, but long press wasn't triggered
            cancelTap()
            return false
        }

        if (isLongPressTriggered) {
            // Long press was already handled
            cleanupTap()
            return true
        }

        // Check if moved too far
        val deltaX = abs(event.x - downX)
        val deltaY = abs(event.y - downY)

        if (deltaX > touchSlop || deltaY > touchSlop) {
            cancelTap()
            return false
        }

        // Valid tap - check for double tap
        return processTap(event)
    }

    private fun handleActionCancel(): Boolean {
        cancelTap()
        return true
    }

    private fun processTap(event: MotionEvent): Boolean {
        val currentTime = event.eventTime
        val isDoubleTap = isValidDoubleTap(event.x, event.y, currentTime)

        if (isDoubleTap && configuration.doubleTapEnabled) {
            // Handle double tap immediately
            tapCount = 0 // Reset count
            lastTapTime = 0
            cleanupTap()
            return callback?.onDoubleTap(event) ?: false
        } else {
            // Potential single tap - delay to check for double tap
            tapCount++
            lastTapTime = currentTime
            lastTapX = event.x
            lastTapY = event.y

            scheduleSingleTap(event)
            cleanupTap()
            return true
        }
    }

    private fun isValidDoubleTap(x: Float, y: Float, time: Long): Boolean {
        if (tapCount == 0 || lastTapTime == 0L) return false

        val timeDelta = time - lastTapTime
        val distanceX = abs(x - lastTapX)
        val distanceY = abs(y - lastTapY)

        return timeDelta <= doubleTapTimeout &&
                distanceX <= doubleTapSlop &&
                distanceY <= doubleTapSlop
    }

    private fun scheduleSingleTap(event: MotionEvent) {
        cancelSingleTap() // Cancel any pending single tap

        pendingSingleTapAction = Runnable {
            if (tapCount > 0) {
                tapCount = 0
                lastTapTime = 0
                callback?.onSingleTap(event)
            }
        }

        handler.postDelayed(pendingSingleTapAction!!, doubleTapTimeout)
    }

    private fun scheduleLongPress(event: MotionEvent) {
        cancelLongPress()

        pendingLongPressAction = Runnable {
            if (isTapInProgress && !isLongPressTriggered) {
                isLongPressTriggered = true
                callback?.onLongPress(event)
            }
        }

        handler.postDelayed(pendingLongPressAction!!, longPressTimeout)
    }

    private fun cancelSingleTap() {
        pendingSingleTapAction?.let { action ->
            handler.removeCallbacks(action)
            pendingSingleTapAction = null
        }
    }

    private fun cancelLongPress() {
        pendingLongPressAction?.let { action ->
            handler.removeCallbacks(action)
            pendingLongPressAction = null
        }
    }

    private fun cancelPendingActions() {
        cancelSingleTap()
        cancelLongPress()
    }

    private fun cancelTap() {
        cancelPendingActions()
        cleanupTap()
    }

    private fun cleanupTap() {
        isTapInProgress = false
        isLongPressTriggered = false
    }

    /**
     * Cancels any ongoing tap detection
     */
    fun cancel() {
        cancelTap()
        tapCount = 0
        lastTapTime = 0
    }

    /**
     * Gets the current tap count (for statistics)
     */
    fun getTapCount(): Int = tapCount

    /**
     * Checks if a tap is currently in progress
     */
    fun isTapInProgress(): Boolean = isTapInProgress

    /**
     * Checks if long press was triggered
     */
    fun isLongPressTriggered(): Boolean = isLongPressTriggered

    /**
     * Gets tap detector statistics
     */
    fun getStatistics(): TapStatistics {
        return TapStatistics(
            isTapInProgress = isTapInProgress,
            isLongPressTriggered = isLongPressTriggered,
            tapCount = tapCount,
            lastTapTime = lastTapTime,
            touchSlop = touchSlop,
            doubleTapSlop = doubleTapSlop,
            longPressTimeout = longPressTimeout,
            doubleTapTimeout = doubleTapTimeout
        )
    }

    data class TapStatistics(
        val isTapInProgress: Boolean,
        val isLongPressTriggered: Boolean,
        val tapCount: Int,
        val lastTapTime: Long,
        val touchSlop: Int,
        val doubleTapSlop: Int,
        val longPressTimeout: Long,
        val doubleTapTimeout: Long
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Tap Detector Statistics:")
                appendLine("  Tap In Progress: $isTapInProgress")
                appendLine("  Long Press Triggered: $isLongPressTriggered")
                appendLine("  Tap Count: $tapCount")
                appendLine("  Last Tap Time: $lastTapTime")
                appendLine("  Touch Slop: ${touchSlop}px")
                appendLine("  Double Tap Slop: ${doubleTapSlop}px")
                appendLine("  Long Press Timeout: ${longPressTimeout}ms")
                appendLine("  Double Tap Timeout: ${doubleTapTimeout}ms")
            }
        }
    }
}