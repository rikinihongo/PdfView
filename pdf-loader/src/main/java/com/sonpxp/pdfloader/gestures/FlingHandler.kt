package com.sonpxp.pdfloader.gestures


import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.sonpxp.pdfloader.ScrollDirection
import com.sonpxp.pdfloader.model.Configuration
import com.sonpxp.pdfloader.utils.MathUtils
import kotlin.math.abs
import kotlin.math.sqrt


/**
 * Handles fling animations and momentum scrolling
 * Uses Android's OverScroller for natural deceleration curves
 */
class FlingHandler(
    private val context: Context,
    private var configuration: Configuration,
) {

    private val scroller = OverScroller(context)
    private val handler = Handler(Looper.getMainLooper())
    private var flingRunnable: FlingRunnable? = null
    private var flingCallback: ((Float, Float) -> Unit)? = null

    // Configuration
    private val minFlingVelocity =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocity =
        ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    // State tracking
    private var isFlingActive = false
    private var startX = 0f
    private var startY = 0f
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f

    // Fling statistics
    private var totalFlingDistance = 0f
    private var flingStartTime = 0L
    private var flingDuration = 0L
    private var estimatedFlingDuration = 0L // Add estimated duration

    private inner class FlingRunnable : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                val newX = scroller.currX.toFloat()
                val newY = scroller.currY.toFloat()

                // Calculate distance traveled
                val deltaX = newX - startX
                val deltaY = newY - startY
                totalFlingDistance += sqrt(deltaX * deltaX + deltaY * deltaY)

                // Update start position for next iteration
                startX = newX
                startY = newY

                // Notify callback
                flingCallback?.invoke(newX, newY)

                // Continue animation
                handler.post(this)
            } else {
                // Fling finished
                completeFling()
            }
        }
    }

    /**
     * Starts a fling animation
     */
    fun startFling(
        velocityX: Float,
        velocityY: Float,
        callback: (Float, Float) -> Unit,
    ) {
        // Stop any existing fling
        stopFling()

        // Check if velocity is significant enough
        val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
        if (speed < minFlingVelocity || !configuration.pageFling) {
            return
        }

        // Clamp velocity to system limits
        val clampedVelocityX = MathUtils.clamp(velocityX, -maxFlingVelocity, maxFlingVelocity)
        val clampedVelocityY = MathUtils.clamp(velocityY, -maxFlingVelocity, maxFlingVelocity)

        // Apply scroll direction constraints
        val constrainedVelocity = applyVelocityConstraints(clampedVelocityX, clampedVelocityY)

        // Set up fling state
        isFlingActive = true
        flingCallback = callback
        flingStartTime = System.currentTimeMillis()
        totalFlingDistance = 0f

        // Estimate fling duration for progress calculation
        estimatedFlingDuration =
            calculateFlingDuration(constrainedVelocity.first, constrainedVelocity.second)

        // Start scroller
        scroller.fling(
            startX.toInt(), startY.toInt(),
            constrainedVelocity.first.toInt(), constrainedVelocity.second.toInt(),
            minX.toInt(), maxX.toInt(),
            minY.toInt(), maxY.toInt(),
            0, 0 // No over-scroll in scroller, handled separately
        )

        // Start animation
        flingRunnable = FlingRunnable()
        handler.post(flingRunnable!!)
    }

    /**
     * Starts a fling with bounds
     */
    fun startFlingWithBounds(
        startX: Float,
        startY: Float,
        velocityX: Float,
        velocityY: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        callback: (Float, Float) -> Unit,
    ) {
        this.startX = startX
        this.startY = startY
        this.minX = minX
        this.maxX = maxX
        this.minY = minY
        this.maxY = maxY

        startFling(velocityX, velocityY, callback)
    }

    /**
     * Stops current fling animation
     */
    fun stopFling() {
        if (isFlingActive) {
            scroller.abortAnimation()
            flingRunnable?.let { runnable ->
                handler.removeCallbacks(runnable)
            }
            completeFling()
        }
    }

    /**
     * Forces fling to finish immediately
     */
    fun forceFinishFling() {
        if (isFlingActive) {
            scroller.forceFinished(true)
            completeFling()
        }
    }

    /**
     * Checks if fling is currently active
     */
    fun isFlingActive(): Boolean = isFlingActive

    /**
     * Gets current fling position
     */
    fun getCurrentPosition(): Pair<Float, Float> {
        return if (isFlingActive) {
            Pair(scroller.currX.toFloat(), scroller.currY.toFloat())
        } else {
            Pair(startX, startY)
        }
    }

    /**
     * Gets current fling velocity
     */
    fun getCurrentVelocity(): Pair<Float, Float> {
        return if (isFlingActive) {
            // OverScroller provides current velocity magnitude but not direction
            // We need to calculate direction based on remaining distance
            val currentVelocity =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    scroller.currVelocity
                } else {
                    // Fallback for older Android versions
                    val progress = getFlingProgress()
                    val initialSpeed = sqrt(
                        (scroller.finalX - scroller.startX) * (scroller.finalX - scroller.startX) +
                                (scroller.finalY - scroller.startY) * (scroller.finalY - scroller.startY).toFloat()
                    ) / (estimatedFlingDuration / 1000f)
                    initialSpeed * (1f - progress) // Simple linear decay
                }

            // Calculate direction based on remaining distance to final position
            val remainingX = scroller.finalX - scroller.currX
            val remainingY = scroller.finalY - scroller.currY
            val remainingDistance =
                sqrt(remainingX * remainingX + remainingY * remainingY.toFloat())

            if (remainingDistance > 0 && currentVelocity > 0) {
                val velocityX = currentVelocity * remainingX / remainingDistance
                val velocityY = currentVelocity * remainingY / remainingDistance
                Pair(velocityX, velocityY)
            } else {
                Pair(0f, 0f)
            }
        } else {
            Pair(0f, 0f)
        }
    }

    /**
     * Gets fling progress (0.0 to 1.0)
     */
    fun getFlingProgress(): Float {
        return if (isFlingActive) {
            val elapsed = System.currentTimeMillis() - flingStartTime
            if (estimatedFlingDuration > 0) {
                MathUtils.clamp(elapsed.toFloat() / estimatedFlingDuration, 0f, 1f)
            } else {
                // Fallback: check if scroller is finished
                if (scroller.isFinished) 1f else 0.5f
            }
        } else {
            1f
        }
    }

    /**
     * Calculates expected fling distance
     */
    fun calculateFlingDistance(velocityX: Float, velocityY: Float): Pair<Float, Float> {
        // Use friction coefficient to estimate distance
        val friction = 0.015f // Android's default scroll friction
        val deceleration = 9.8f * 160f * friction // Pixels per second^2

        val timeX = abs(velocityX) / deceleration
        val timeY = abs(velocityY) / deceleration

        val distanceX = velocityX * timeX - 0.5f * deceleration * timeX * timeX
        val distanceY = velocityY * timeY - 0.5f * deceleration * timeY * timeY

        return Pair(distanceX, distanceY)
    }

    /**
     * Calculates fling duration for given velocity
     */
    fun calculateFlingDuration(velocityX: Float, velocityY: Float): Long {
        val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
        val friction = 0.015f
        val deceleration = 9.8f * 160f * friction

        return (speed / deceleration * 1000).toLong().coerceAtMost(2000) // Max 2 seconds
    }

    /**
     * Sets fling bounds for scroll area
     */
    fun setBounds(minX: Float, maxX: Float, minY: Float, maxY: Float) {
        this.minX = minX
        this.maxX = maxX
        this.minY = minY
        this.maxY = maxY
    }

    /**
     * Sets current position (call before starting fling)
     */
    fun setCurrentPosition(x: Float, y: Float) {
        startX = x
        startY = y
    }

    /**
     * Checks if velocity is significant enough for fling
     */
    fun hasSignificantVelocity(velocityX: Float, velocityY: Float): Boolean {
        val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
        return speed > minFlingVelocity
    }

    /**
     * Gets remaining fling distance
     */
    fun getRemainingDistance(): Pair<Float, Float> {
        return if (isFlingActive) {
            val remainingX = scroller.finalX - scroller.currX
            val remainingY = scroller.finalY - scroller.currY
            Pair(remainingX.toFloat(), remainingY.toFloat())
        } else {
            Pair(0f, 0f)
        }
    }

    /**
     * Gets total fling distance traveled so far
     */
    fun getTotalDistance(): Float = totalFlingDistance

    /**
     * Gets fling final position
     */
    fun getFinalPosition(): Pair<Float, Float> {
        return if (isFlingActive) {
            Pair(scroller.finalX.toFloat(), scroller.finalY.toFloat())
        } else {
            getCurrentPosition()
        }
    }

    /**
     * Interrupts fling at current position
     */
    fun interruptFling() {
        if (isFlingActive) {
            val currentPos = getCurrentPosition()
            stopFling()
            startX = currentPos.first
            startY = currentPos.second
        }
    }

    private fun completeFling() {
        isFlingActive = false
        flingDuration = System.currentTimeMillis() - flingStartTime
        flingRunnable = null
        flingCallback = null
    }

    private fun applyVelocityConstraints(velocityX: Float, velocityY: Float): Pair<Float, Float> {
        return when (configuration.pageScrollDirection) {
            ScrollDirection.HORIZONTAL -> Pair(velocityX, 0f)
            ScrollDirection.VERTICAL -> Pair(0f, velocityY)
            ScrollDirection.BOTH -> Pair(velocityX, velocityY)
        }
    }

    /**
     * Updates configuration
     */
    fun updateConfiguration(newConfig: Configuration) {
        configuration = newConfig

        // Stop current fling if fling is disabled
        if (!configuration.pageFling && isFlingActive) {
            stopFling()
        }
    }

    /**
     * Gets fling handler statistics
     */
    fun getStatistics(): FlingStatistics {
        val currentPos = getCurrentPosition()
        val currentVel = getCurrentVelocity()
        val remainingDist = getRemainingDistance()
        val finalPos = getFinalPosition()

        return FlingStatistics(
            isActive = isFlingActive,
            currentPosition = currentPos,
            currentVelocity = currentVel,
            progress = getFlingProgress(),
            totalDistance = totalFlingDistance,
            remainingDistance = remainingDist,
            finalPosition = finalPos,
            duration = if (isFlingActive) System.currentTimeMillis() - flingStartTime else flingDuration,
            bounds = FlingBounds(minX, maxX, minY, maxY),
            minFlingVelocity = minFlingVelocity,
            maxFlingVelocity = maxFlingVelocity
        )
    }

    data class FlingBounds(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float,
    ) {
        fun contains(x: Float, y: Float): Boolean {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
        }

        fun getWidth(): Float = maxX - minX
        fun getHeight(): Float = maxY - minY
    }

    data class FlingStatistics(
        val isActive: Boolean,
        val currentPosition: Pair<Float, Float>,
        val currentVelocity: Pair<Float, Float>,
        val progress: Float,
        val totalDistance: Float,
        val remainingDistance: Pair<Float, Float>,
        val finalPosition: Pair<Float, Float>,
        val duration: Long,
        val bounds: FlingBounds,
        val minFlingVelocity: Float,
        val maxFlingVelocity: Float,
    ) {
        fun getSpeedMagnitude(): Float {
            return sqrt(
                currentVelocity.first * currentVelocity.first +
                        currentVelocity.second * currentVelocity.second
            )
        }

        fun getRemainingDistanceMagnitude(): Float {
            return sqrt(
                remainingDistance.first * remainingDistance.first +
                        remainingDistance.second * remainingDistance.second
            )
        }

        override fun toString(): String {
            return buildString {
                appendLine("Fling Handler Statistics:")
                appendLine("  Active: $isActive")
                appendLine("  Position: (${currentPosition.first.toInt()}, ${currentPosition.second.toInt()})")
                appendLine("  Velocity: (${currentVelocity.first.toInt()}, ${currentVelocity.second.toInt()}) px/s")
                appendLine("  Speed: ${getSpeedMagnitude().toInt()} px/s")
                appendLine("  Progress: ${(progress * 100).toInt()}%")
                appendLine("  Distance: ${totalDistance.toInt()}px traveled")
                appendLine("  Remaining: ${getRemainingDistanceMagnitude().toInt()}px")
                appendLine("  Final Position: (${finalPosition.first.toInt()}, ${finalPosition.second.toInt()})")
                appendLine("  Duration: ${duration}ms")
                appendLine("  Bounds: (${bounds.minX.toInt()}, ${bounds.minY.toInt()}) to (${bounds.maxX.toInt()}, ${bounds.maxY.toInt()})")
                appendLine("  Velocity Range: ${minFlingVelocity.toInt()} - ${maxFlingVelocity.toInt()} px/s")
            }
        }
    }
}