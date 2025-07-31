package com.sonpxp.pdfloader.gestures


import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.sonpxp.pdfloader.ScrollDirection
import com.sonpxp.pdfloader.model.Configuration
import kotlin.math.abs

/**
 * Detects drag/pan gestures
 * Handles single-finger dragging with proper thresholds and velocity tracking
 */
class DragDetector(
    private val context: Context,
    private val configuration: Configuration
) {

    interface DragCallback {
        fun onDragStart(startX: Float, startY: Float): Boolean
        fun onDrag(deltaX: Float, deltaY: Float, totalX: Float, totalY: Float): Boolean
        fun onDragEnd(velocityX: Float, velocityY: Float)
    }

    private var callback: DragCallback? = null

    // Configuration
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minimumVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maximumVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    // State tracking
    private var isDragging = false
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var totalDeltaX = 0f
    private var totalDeltaY = 0f
    private var velocityX = 0f
    private var velocityY = 0f

    // Movement history for velocity calculation
    private val movementHistory = mutableListOf<MovementSample>()
    private val maxHistorySize = 20
    private val velocityTimeWindow = 100L // 100ms

    data class MovementSample(
        val x: Float,
        val y: Float,
        val time: Long
    )

    fun setCallbacks(callback: DragCallback) {
        this.callback = callback
    }

    /**
     * Processes touch events for drag detection
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
        // Initialize drag state
        startX = event.x
        startY = event.y
        lastX = event.x
        lastY = event.y
        totalDeltaX = 0f
        totalDeltaY = 0f
        velocityX = 0f
        velocityY = 0f
        isDragging = false

        // Clear movement history
        movementHistory.clear()
        addMovementSample(event.x, event.y, event.eventTime)

        return true
    }

    private fun handleActionMove(event: MotionEvent): Boolean {
        val currentX = event.x
        val currentY = event.y

        // Add to movement history
        addMovementSample(currentX, currentY, event.eventTime)

        if (!isDragging) {
            // Check if we've moved beyond touch slop
            val deltaX = currentX - startX
            val deltaY = currentY - startY
            val distance = abs(deltaX) + abs(deltaY) // Manhattan distance for performance

            if (distance > touchSlop) {
                // Start dragging
                isDragging = true
                val startHandled = callback?.onDragStart(startX, startY) ?: false

                if (!startHandled) {
                    // Callback rejected drag start
                    isDragging = false
                    return false
                }

                // Update last position to current for delta calculation
                lastX = currentX
                lastY = currentY
                return true
            }
        } else {
            // Continue dragging
            val deltaX = currentX - lastX
            val deltaY = currentY - lastY

            // Apply direction constraints if configured
            val constrainedDelta = applyScrollConstraints(deltaX, deltaY)

            totalDeltaX += constrainedDelta.first
            totalDeltaY += constrainedDelta.second

            // Update velocity
            updateVelocity()

            // Notify callback
            val handled = callback?.onDrag(
                constrainedDelta.first,
                constrainedDelta.second,
                totalDeltaX,
                totalDeltaY
            ) ?: false

            // Update last position
            lastX = currentX
            lastY = currentY

            return handled
        }

        return false
    }

    private fun handleActionUp(event: MotionEvent): Boolean {
        if (isDragging) {
            // Add final movement sample
            addMovementSample(event.x, event.y, event.eventTime)
            updateVelocity()

            // Notify callback
            callback?.onDragEnd(velocityX, velocityY)

            reset()
            return true
        }

        reset()
        return false
    }

    private fun handleActionCancel(): Boolean {
        if (isDragging) {
            // Treat cancel as drag end with zero velocity
            callback?.onDragEnd(0f, 0f)
        }

        reset()
        return true
    }

    private fun applyScrollConstraints(deltaX: Float, deltaY: Float): Pair<Float, Float> {
        return when (configuration.pageScrollDirection) {
            ScrollDirection.HORIZONTAL -> {
                Pair(deltaX, 0f)
            }
            ScrollDirection.VERTICAL -> {
                Pair(0f, deltaY)
            }
            ScrollDirection.BOTH -> {
                Pair(deltaX, deltaY)
            }
        }
    }

    private fun addMovementSample(x: Float, y: Float, time: Long) {
        movementHistory.add(MovementSample(x, y, time))

        // Remove old samples beyond time window
        val cutoffTime = time - velocityTimeWindow
        movementHistory.removeAll { it.time < cutoffTime }

        // Limit history size
        while (movementHistory.size > maxHistorySize) {
            movementHistory.removeAt(0)
        }
    }

    private fun updateVelocity() {
        if (movementHistory.size < 2) {
            velocityX = 0f
            velocityY = 0f
            return
        }

        val newest = movementHistory.last()
        val oldest = movementHistory.first()

        val timeDelta = newest.time - oldest.time
        if (timeDelta > 0) {
            velocityX = (newest.x - oldest.x) * 1000 / timeDelta // pixels per second
            velocityY = (newest.y - oldest.y) * 1000 / timeDelta

            // Clamp velocity to system limits
            velocityX = velocityX.coerceIn(-maximumVelocity, maximumVelocity)
            velocityY = velocityY.coerceIn(-maximumVelocity, maximumVelocity)
        }
    }

    private fun reset() {
        isDragging = false
        startX = 0f
        startY = 0f
        lastX = 0f
        lastY = 0f
        totalDeltaX = 0f
        totalDeltaY = 0f
        velocityX = 0f
        velocityY = 0f
        movementHistory.clear()
    }

    /**
     * Sets final velocity externally (called from gesture controller)
     */
    fun setFinalVelocity(vx: Float, vy: Float) {
        velocityX = vx
        velocityY = vy
    }

    /**
     * Cancels current drag operation
     */
    fun cancel() {
        if (isDragging) {
            callback?.onDragEnd(0f, 0f)
        }
        reset()
    }

    /**
     * Checks if drag is currently in progress
     */
    fun isDragging(): Boolean = isDragging

    /**
     * Gets current drag delta since start
     */
    fun getTotalDelta(): Pair<Float, Float> = Pair(totalDeltaX, totalDeltaY)

    /**
     * Gets current velocity
     */
    fun getVelocity(): Pair<Float, Float> = Pair(velocityX, velocityY)

    /**
     * Gets drag start position
     */
    fun getStartPosition(): Pair<Float, Float> = Pair(startX, startY)

    /**
     * Checks if velocity is significant enough for fling
     */
    fun hasSignificantVelocity(): Boolean {
        val speed = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)
        return speed > minimumVelocity
    }

    /**
     * Gets drag detector statistics
     */
    fun getStatistics(): DragStatistics {
        return DragStatistics(
            isDragging = isDragging,
            startPosition = Pair(startX, startY),
            currentPosition = Pair(lastX, lastY),
            totalDelta = Pair(totalDeltaX, totalDeltaY),
            velocity = Pair(velocityX, velocityY),
            movementSamples = movementHistory.size,
            touchSlop = touchSlop,
            hasSignificantVelocity = hasSignificantVelocity()
        )
    }

    data class DragStatistics(
        val isDragging: Boolean,
        val startPosition: Pair<Float, Float>,
        val currentPosition: Pair<Float, Float>,
        val totalDelta: Pair<Float, Float>,
        val velocity: Pair<Float, Float>,
        val movementSamples: Int,
        val touchSlop: Float,
        val hasSignificantVelocity: Boolean
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Drag Detector Statistics:")
                appendLine("  Is Dragging: $isDragging")
                appendLine("  Start: (${startPosition.first.toInt()}, ${startPosition.second.toInt()})")
                appendLine("  Current: (${currentPosition.first.toInt()}, ${currentPosition.second.toInt()})")
                appendLine("  Total Delta: (${totalDelta.first.toInt()}, ${totalDelta.second.toInt()})")
                appendLine("  Velocity: (${velocity.first.toInt()}, ${velocity.second.toInt()}) px/s")
                appendLine("  Movement Samples: $movementSamples")
                appendLine("  Touch Slop: ${touchSlop.toInt()}px")
                appendLine("  Significant Velocity: $hasSignificantVelocity")
            }
        }
    }
}