package com.sonpxp.pdfloader.animation


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.PointF
import android.view.animation.Interpolator
import kotlin.math.abs
import kotlin.math.min

/**
 * Handles zoom animations for the PDF viewer
 * Provides smooth zoom transitions with various animation styles
 */
class ZoomAnimator(
    private val config: AnimationConfig = AnimationConfig.defaultZoomAnimation(),
) {

    private var currentZoomAnimation: ValueAnimator? = null
    private val zoomListeners = mutableListOf<ZoomAnimationListener>()

    /**
     * Interface for listening to zoom animation events
     */
    interface ZoomAnimationListener {
        fun onZoomAnimationStart(startZoom: Float, endZoom: Float, focusPoint: PointF?) {}
        fun onZoomAnimationUpdate(currentZoom: Float, progress: Float, focusPoint: PointF?) {}
        fun onZoomAnimationEnd(endZoom: Float, focusPoint: PointF?) {}
        fun onZoomAnimationCancel() {}
    }

    /**
     * Animates zoom from current level to target level
     * @param startZoom starting zoom level
     * @param endZoom target zoom level
     * @param focusPoint optional focus point for zoom (null for center zoom)
     * @param duration animation duration in milliseconds (optional, uses config if null)
     * @param interpolator animation interpolator (optional, uses config if null)
     * @param onUpdate callback for each animation frame with current zoom level
     * @param onComplete callback when animation completes
     */
    fun animateZoom(
        startZoom: Float,
        endZoom: Float,
        focusPoint: PointF? = null,
        duration: Long? = null,
        interpolator: Interpolator? = null,
        onUpdate: ((Float, PointF?) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!config.enabled || !config.animateZoom) {
            onUpdate?.invoke(endZoom, focusPoint)
            onComplete?.invoke()
            return
        }

        // Cancel any existing zoom animation
        cancelZoomAnimation()

        // Skip animation for very small zoom changes
        val zoomDelta = abs(endZoom - startZoom)
        if (zoomDelta < 0.01f) {
            onUpdate?.invoke(endZoom, focusPoint)
            onComplete?.invoke()
            return
        }

        // Notify listeners
        zoomListeners.forEach { it.onZoomAnimationStart(startZoom, endZoom, focusPoint) }

        // Create and configure the animation
        currentZoomAnimation = ValueAnimator.ofFloat(startZoom, endZoom).apply {
            this.duration = duration ?: calculateZoomDuration(zoomDelta)
            this.interpolator = interpolator ?: config.interpolator

            addUpdateListener { animator ->
                val currentZoom = animator.animatedValue as Float
                val progress = animator.animatedFraction

                onUpdate?.invoke(currentZoom, focusPoint)
                zoomListeners.forEach {
                    it.onZoomAnimationUpdate(
                        currentZoom,
                        progress,
                        focusPoint
                    )
                }
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentZoomAnimation = null
                    zoomListeners.forEach { it.onZoomAnimationEnd(endZoom, focusPoint) }
                    onComplete?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentZoomAnimation = null
                    zoomListeners.forEach { it.onZoomAnimationCancel() }
                }
            })

            start()
        }
    }

    /**
     * Animates double-tap zoom with bounce effect
     * @param currentZoom current zoom level
     * @param targetZoom target zoom level (usually double-tap zoom scale)
     * @param minZoom minimum allowed zoom level
     * @param maxZoom maximum allowed zoom level
     * @param focusPoint focus point for the zoom
     * @param onUpdate callback for zoom updates
     * @param onComplete callback when animation completes
     */
    fun animateDoubleTapZoom(
        currentZoom: Float,
        targetZoom: Float,
        minZoom: Float,
        maxZoom: Float,
        focusPoint: PointF,
        onUpdate: ((Float, PointF) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        // Determine actual target zoom based on current state
        val actualTargetZoom = when {
            currentZoom >= targetZoom -> minZoom // Zoom out to minimum
            else -> targetZoom.coerceIn(minZoom, maxZoom) // Zoom to target
        }

        animateZoom(
            startZoom = currentZoom,
            endZoom = actualTargetZoom,
            focusPoint = focusPoint,
            duration = config.getEffectiveDuration(),
            interpolator = Interpolators.BACK, // Bounce effect for double-tap
            onUpdate = { zoom, focus -> onUpdate?.invoke(zoom, focus ?: focusPoint) },
            onComplete = onComplete
        )
    }

    /**
     * Animates zoom to fit content
     * @param currentZoom current zoom level
     * @param fitZoom calculated zoom level to fit content
     * @param animationType type of fit animation
     * @param onUpdate callback for zoom updates
     * @param onComplete callback when animation completes
     */
    fun animateZoomToFit(
        currentZoom: Float,
        fitZoom: Float,
        animationType: ZoomToFitType = ZoomToFitType.SMOOTH,
        onUpdate: ((Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        val (duration, interpolator) = when (animationType) {
            ZoomToFitType.SMOOTH -> Pair(
                config.getEffectiveDuration(),
                Interpolators.MATERIAL_FAST_OUT_SLOW_IN
            )

            ZoomToFitType.QUICK -> Pair(
                config.getEffectiveDuration() / 2,
                Interpolators.MATERIAL_FAST_OUT_LINEAR_IN
            )

            ZoomToFitType.ELASTIC -> Pair(
                config.getEffectiveDuration() * 2,
                Interpolators.ELASTIC
            )
        }

        animateZoom(
            startZoom = currentZoom,
            endZoom = fitZoom,
            focusPoint = null, // Center zoom for fit
            duration = duration,
            interpolator = interpolator,
            onUpdate = { zoom, _ -> onUpdate?.invoke(zoom) },
            onComplete = onComplete
        )
    }

    /**
     * Animates pinch zoom with momentum
     * @param startZoom starting zoom level
     * @param endZoom ending zoom level from pinch gesture
     * @param velocity zoom velocity from gesture
     * @param minZoom minimum allowed zoom level
     * @param maxZoom maximum allowed zoom level
     * @param focusPoint focus point for the zoom
     * @param onUpdate callback for zoom updates
     * @param onComplete callback when animation completes
     */
    fun animatePinchZoomMomentum(
        startZoom: Float,
        endZoom: Float,
        velocity: Float,
        minZoom: Float,
        maxZoom: Float,
        focusPoint: PointF,
        onUpdate: ((Float, PointF) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        // Calculate momentum target
        val momentumTarget = endZoom + velocity * 0.3f // Adjust momentum factor as needed
        val clampedTarget = momentumTarget.coerceIn(minZoom, maxZoom)

        // Calculate duration based on velocity
        val velocityFactor = min(2.0f, abs(velocity) / 2.0f)
        val duration = (config.getEffectiveDuration() * (0.5f + 0.5f * velocityFactor)).toLong()

        animateZoom(
            startZoom = endZoom,
            endZoom = clampedTarget,
            focusPoint = focusPoint,
            duration = duration,
            interpolator = Interpolators.ExponentialInterpolator(
                exponent = 2.0f,
                direction = Interpolators.ExponentialInterpolator.Direction.EASE_OUT
            ),
            onUpdate = { zoom, focus -> onUpdate?.invoke(zoom, focus ?: focusPoint) },
            onComplete = onComplete
        )
    }

    /**
     * Animates zoom bounds correction (when zoom goes beyond limits)
     * @param currentZoom current zoom level (potentially out of bounds)
     * @param targetZoom corrected zoom level within bounds
     * @param focusPoint focus point for the correction
     * @param onUpdate callback for zoom updates
     * @param onComplete callback when animation completes
     */
    fun animateZoomBoundsCorrection(
        currentZoom: Float,
        targetZoom: Float,
        focusPoint: PointF? = null,
        onUpdate: ((Float, PointF?) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        animateZoom(
            startZoom = currentZoom,
            endZoom = targetZoom,
            focusPoint = focusPoint,
            duration = config.getEffectiveDuration() / 2, // Quick correction
            interpolator = Interpolators.SPRING, // Spring back effect
            onUpdate = onUpdate,
            onComplete = onComplete
        )
    }

    /**
     * Animates smooth zoom transition with scale and translation
     * @param startZoom starting zoom level
     * @param endZoom target zoom level
     * @param startTranslation starting translation point
     * @param endTranslation target translation point
     * @param onUpdate callback with zoom and translation updates
     * @param onComplete callback when animation completes
     */
    fun animateZoomWithTranslation(
        startZoom: Float,
        endZoom: Float,
        startTranslation: PointF,
        endTranslation: PointF,
        onUpdate: ((Float, PointF) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!config.enabled) {
            onUpdate?.invoke(endZoom, endTranslation)
            onComplete?.invoke()
            return
        }

        cancelZoomAnimation()

        currentZoomAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = config.getEffectiveDuration()
            interpolator = config.interpolator

            addUpdateListener { animator ->
                val progress = animator.animatedFraction

                val currentZoom = startZoom + (endZoom - startZoom) * progress
                val currentTranslation = PointF(
                    startTranslation.x + (endTranslation.x - startTranslation.x) * progress,
                    startTranslation.y + (endTranslation.y - startTranslation.y) * progress
                )

                onUpdate?.invoke(currentZoom, currentTranslation)
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentZoomAnimation = null
                    onComplete?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentZoomAnimation = null
                }
            })

            start()
        }
    }

    /**
     * Cancels the current zoom animation
     */
    fun cancelZoomAnimation() {
        currentZoomAnimation?.cancel()
        currentZoomAnimation = null
    }

    /**
     * Checks if a zoom animation is currently running
     */
    fun isZoomAnimating(): Boolean {
        return currentZoomAnimation?.isRunning == true
    }

    /**
     * Adds a listener for zoom animation events
     */
    fun addZoomListener(listener: ZoomAnimationListener) {
        zoomListeners.add(listener)
    }

    /**
     * Removes a listener for zoom animation events
     */
    fun removeZoomListener(listener: ZoomAnimationListener) {
        zoomListeners.remove(listener)
    }

    /**
     * Clears all zoom animation listeners
     */
    fun clearZoomListeners() {
        zoomListeners.clear()
    }

    // Private helper methods

    private fun calculateZoomDuration(zoomDelta: Float): Long {
        // Base duration calculation - larger zoom changes take longer
        val baseDuration = config.getEffectiveDuration()
        val zoomFactor = min(2.0f, zoomDelta * 2f) // Cap at 2x duration
        return (baseDuration * (0.3f + 0.7f * zoomFactor)).toLong()
    }

    /**
     * Types of zoom-to-fit animations
     */
    enum class ZoomToFitType {
        SMOOTH,
        QUICK,
        ELASTIC
    }
}