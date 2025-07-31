package com.sonpxp.pdfloader.animation


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.Interpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Handles scroll animations for the PDF viewer
 * Provides smooth scrolling animations with various easing options
 */
class ScrollAnimator(
    private val config: AnimationConfig = AnimationConfig.defaultPageTransition()
) {

    private var currentScrollAnimation: ValueAnimator? = null
    private val scrollListeners = mutableListOf<ScrollAnimationListener>()

    /**
     * Interface for listening to scroll animation events
     */
    interface ScrollAnimationListener {
        fun onScrollAnimationStart(startX: Float, startY: Float, endX: Float, endY: Float) {}
        fun onScrollAnimationUpdate(currentX: Float, currentY: Float, progress: Float) {}
        fun onScrollAnimationEnd(endX: Float, endY: Float) {}
        fun onScrollAnimationCancel() {}
    }

    /**
     * Smoothly scrolls from current position to target position
     * @param startX starting X position
     * @param startY starting Y position
     * @param endX target X position
     * @param endY target Y position
     * @param duration animation duration in milliseconds (optional, uses config if null)
     * @param interpolator animation interpolator (optional, uses config if null)
     * @param onUpdate callback for each animation frame with current position
     * @param onComplete callback when animation completes
     */
    fun animateScroll(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long? = null,
        interpolator: Interpolator? = null,
        onUpdate: ((Float, Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        if (!config.enabled || !config.animateScroll) {
            onUpdate?.invoke(endX, endY)
            onComplete?.invoke()
            return
        }

        // Cancel any existing scroll animation
        cancelScrollAnimation()

        // Calculate distance to determine if animation is needed
        val deltaX = abs(endX - startX)
        val deltaY = abs(endY - startY)
        val totalDistance = max(deltaX, deltaY)

        // Skip animation for very small distances
        if (totalDistance < 1f) {
            onUpdate?.invoke(endX, endY)
            onComplete?.invoke()
            return
        }

        // Notify listeners
        scrollListeners.forEach { it.onScrollAnimationStart(startX, startY, endX, endY) }

        // Create and configure the animation
        currentScrollAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration ?: calculateDuration(totalDistance)
            this.interpolator = interpolator ?: config.interpolator

            addUpdateListener { animator ->
                val progress = animator.animatedFraction
                val currentX = startX + (endX - startX) * progress
                val currentY = startY + (endY - startY) * progress

                onUpdate?.invoke(currentX, currentY)
                scrollListeners.forEach { it.onScrollAnimationUpdate(currentX, currentY, progress) }
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentScrollAnimation = null
                    scrollListeners.forEach { it.onScrollAnimationEnd(endX, endY) }
                    onComplete?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentScrollAnimation = null
                    scrollListeners.forEach { it.onScrollAnimationCancel() }
                }
            })

            start()
        }
    }

    /**
     * Animates scroll to a specific page with smooth transition
     * @param currentScrollX current horizontal scroll position
     * @param currentScrollY current vertical scroll position
     * @param targetPageX target page X position
     * @param targetPageY target page Y position
     * @param pageTransitionType type of page transition (affects animation style)
     * @param onUpdate callback for scroll updates
     * @param onComplete callback when animation completes
     */
    fun animateScrollToPage(
        currentScrollX: Float,
        currentScrollY: Float,
        targetPageX: Float,
        targetPageY: Float,
        pageTransitionType: PageTransitionType = PageTransitionType.SMOOTH,
        onUpdate: ((Float, Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val interpolator = when (pageTransitionType) {
            PageTransitionType.SMOOTH -> Interpolators.MATERIAL_FAST_OUT_SLOW_IN
            PageTransitionType.QUICK -> Interpolators.MATERIAL_FAST_OUT_LINEAR_IN
            PageTransitionType.ELASTIC -> Interpolators.ELASTIC
            PageTransitionType.BOUNCE -> Interpolators.BOUNCE_ADVANCED
        }

        val duration = when (pageTransitionType) {
            PageTransitionType.SMOOTH -> config.getEffectiveDuration()
            PageTransitionType.QUICK -> config.getEffectiveDuration() / 2
            PageTransitionType.ELASTIC -> config.getEffectiveDuration() * 2
            PageTransitionType.BOUNCE -> config.getEffectiveDuration() * 1.5
        }

        animateScroll(
            startX = currentScrollX,
            startY = currentScrollY,
            endX = targetPageX,
            endY = targetPageY,
            duration = duration.toLong(),
            interpolator = interpolator,
            onUpdate = onUpdate,
            onComplete = onComplete
        )
    }

    /**
     * Animates a fling scroll with deceleration
     * @param startX starting X position
     * @param startY starting Y position
     * @param velocityX initial X velocity
     * @param velocityY initial Y velocity
     * @param minX minimum X boundary
     * @param maxX maximum X boundary
     * @param minY minimum Y boundary
     * @param maxY maximum Y boundary
     * @param friction deceleration friction (0.0 to 1.0)
     * @param onUpdate callback for scroll updates
     * @param onComplete callback when animation completes
     */
    fun animateFling(
        startX: Float,
        startY: Float,
        velocityX: Float,
        velocityY: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        friction: Float = 0.05f,
        onUpdate: ((Float, Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        if (!config.enabled || (!config.animateScroll && !config.animatePageTransitions)) {
            onComplete?.invoke()
            return
        }

        // Calculate fling destination
        val flingDistance = calculateFlingDistance(velocityX, velocityY, friction)
        val endX = (startX + flingDistance.first).coerceIn(minX, maxX)
        val endY = (startY + flingDistance.second).coerceIn(minY, maxY)

        // Calculate duration based on velocity
        val velocity = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)
        val duration = calculateFlingDuration(velocity, friction)

        animateScroll(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            duration = duration,
            interpolator = Interpolators.ExponentialInterpolator(
                exponent = 2.0f,
                direction = Interpolators.ExponentialInterpolator.Direction.EASE_OUT
            ),
            onUpdate = onUpdate,
            onComplete = onComplete
        )
    }

    /**
     * Animates scroll with momentum and snap-to-page behavior
     * @param startX starting X position
     * @param startY starting Y position
     * @param velocityX initial X velocity
     * @param velocityY initial Y velocity
     * @param pageWidth width of each page
     * @param pageHeight height of each page
     * @param snapThreshold threshold for snapping (0.0 to 1.0)
     * @param onUpdate callback for scroll updates
     * @param onComplete callback when animation completes
     */
    fun animateScrollWithSnap(
        startX: Float,
        startY: Float,
        velocityX: Float,
        velocityY: Float,
        pageWidth: Float,
        pageHeight: Float,
        snapThreshold: Float = 0.3f,
        onUpdate: ((Float, Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        // Calculate natural fling destination
        val flingDistance = calculateFlingDistance(velocityX, velocityY, 0.05f)
        val naturalEndX = startX + flingDistance.first
        val naturalEndY = startY + flingDistance.second

        // Calculate snap positions
        val snapX = calculateSnapPosition(naturalEndX, pageWidth, snapThreshold)
        val snapY = calculateSnapPosition(naturalEndY, pageHeight, snapThreshold)

        // Use elastic interpolator for snap effect
        animateScroll(
            startX = startX,
            startY = startY,
            endX = snapX,
            endY = snapY,
            duration = config.getEffectiveDuration(),
            interpolator = Interpolators.ELASTIC,
            onUpdate = onUpdate,
            onComplete = onComplete
        )
    }

    /**
     * Cancels the current scroll animation
     */
    fun cancelScrollAnimation() {
        currentScrollAnimation?.cancel()
        currentScrollAnimation = null
    }

    /**
     * Checks if a scroll animation is currently running
     */
    fun isScrollAnimating(): Boolean {
        return currentScrollAnimation?.isRunning == true
    }

    /**
     * Adds a listener for scroll animation events
     */
    fun addScrollListener(listener: ScrollAnimationListener) {
        scrollListeners.add(listener)
    }

    /**
     * Removes a listener for scroll animation events
     */
    fun removeScrollListener(listener: ScrollAnimationListener) {
        scrollListeners.remove(listener)
    }

    /**
     * Clears all scroll animation listeners
     */
    fun clearScrollListeners() {
        scrollListeners.clear()
    }

    // Private helper methods

    private fun calculateDuration(distance: Float): Long {
        // Base duration calculation - longer distances take longer
        val baseDuration = config.getEffectiveDuration()
        val distanceFactor = min(2.0f, distance / 1000f) // Cap at 2x duration
        return (baseDuration * (0.5f + 0.5f * distanceFactor)).toLong()
    }

    private fun calculateFlingDistance(velocityX: Float, velocityY: Float, friction: Float): Pair<Float, Float> {
        val frictionFactor = 1f - friction
        val timeToStop = 60 // Approximate frames to stop

        val distanceX = velocityX * timeToStop * frictionFactor / 2
        val distanceY = velocityY * timeToStop * frictionFactor / 2

        return Pair(distanceX, distanceY)
    }

    private fun calculateFlingDuration(velocity: Float, friction: Float): Long {
        val baseDuration = config.getEffectiveDuration()
        val velocityFactor = min(3.0f, velocity / 1000f) // Cap at 3x duration
        return (baseDuration * (0.3f + 0.7f * velocityFactor)).toLong()
    }

    private fun calculateSnapPosition(position: Float, pageSize: Float, threshold: Float): Float {
        val pageIndex = (position / pageSize).toInt()
        val remainder = (position % pageSize) / pageSize

        return when {
            remainder < threshold -> pageIndex * pageSize
            remainder > (1f - threshold) -> (pageIndex + 1) * pageSize
            else -> position // Don't snap
        }
    }

    /**
     * Types of page transition animations
     */
    enum class PageTransitionType {
        SMOOTH,
        QUICK,
        ELASTIC,
        BOUNCE
    }
}