package com.sonpxp.pdfloader.animation


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import com.sonpxp.pdfloader.AnimationType

/**
 * Handles page transition animations for the PDF viewer
 * Provides various animation types for smooth page changes
 */
class PageAnimator(
    private val config: AnimationConfig = AnimationConfig.defaultPageTransition(),
) {

    private var currentAnimation: Animator? = null
    private val animationListeners = mutableListOf<PageAnimationListener>()

    /**
     * Interface for listening to page animation events
     */
    interface PageAnimationListener {
        fun onAnimationStart(fromPage: Int, toPage: Int) {}
        fun onAnimationEnd(fromPage: Int, toPage: Int) {}
        fun onAnimationCancel(fromPage: Int, toPage: Int) {}
        fun onAnimationProgress(fromPage: Int, toPage: Int, progress: Float) {}
    }

    /**
     * Animates transition from one page to another
     * @param fromView the view of the current page
     * @param toView the view of the target page
     * @param fromPage the current page number
     * @param toPage the target page number
     * @param direction the direction of the transition (1 for forward, -1 for backward)
     * @param onComplete callback when animation completes
     */
    fun animatePageTransition(
        fromView: View?,
        toView: View?,
        fromPage: Int,
        toPage: Int,
        direction: Int = if (toPage > fromPage) 1 else -1,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!config.enabled || config.animationType == AnimationType.NONE) {
            onComplete?.invoke()
            return
        }

        // Cancel any existing animation
        cancelCurrentAnimation()

        // Notify listeners
        animationListeners.forEach { it.onAnimationStart(fromPage, toPage) }

        // Create the appropriate animation
        currentAnimation = when (config.animationType) {
            AnimationType.SLIDE -> createSlideAnimation(fromView, toView, direction)
            AnimationType.FADE -> createFadeAnimation(fromView, toView)
            AnimationType.SCALE -> createScaleAnimation(fromView, toView)
            AnimationType.FLIP -> createFlipAnimation(fromView, toView, direction)
            AnimationType.NONE -> null
        }

        currentAnimation?.let { animator ->
            animator.duration = config.getEffectiveDuration()
            animator.interpolator = config.interpolator

            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimation = null
                    animationListeners.forEach { it.onAnimationEnd(fromPage, toPage) }
                    onComplete?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentAnimation = null
                    animationListeners.forEach { it.onAnimationCancel(fromPage, toPage) }
                }
            })

            if (animator is ValueAnimator) {
                animator.addUpdateListener { valueAnimator ->
                    val progress = valueAnimator.animatedFraction
                    animationListeners.forEach {
                        it.onAnimationProgress(
                            fromPage,
                            toPage,
                            progress
                        )
                    }
                }
            }

            animator.start()
        } ?: run {
            onComplete?.invoke()
        }
    }

    /**
     * Creates a slide animation
     */
    private fun createSlideAnimation(fromView: View?, toView: View?, direction: Int): Animator? {
        if (fromView == null || toView == null) return null

        val animatorSet = AnimatorSet()
        val distance = config.slideDistance * direction

        // Animate outgoing view
        val fromAnimator = if (config.slideFromLeft) {
            ObjectAnimator.ofFloat(fromView, "translationX", 0f, -distance)
        } else {
            ObjectAnimator.ofFloat(fromView, "translationY", 0f, -distance)
        }

        // Prepare incoming view
        if (config.slideFromLeft) {
            toView.translationX = distance
            toView.visibility = View.VISIBLE
        } else {
            toView.translationY = distance
            toView.visibility = View.VISIBLE
        }

        // Animate incoming view
        val toAnimator = if (config.slideFromLeft) {
            ObjectAnimator.ofFloat(toView, "translationX", distance, 0f)
        } else {
            ObjectAnimator.ofFloat(toView, "translationY", distance, 0f)
        }

        animatorSet.playTogether(fromAnimator, toAnimator)
        return animatorSet
    }

    /**
     * Creates a fade animation
     */
    private fun createFadeAnimation(fromView: View?, toView: View?): Animator? {
        if (fromView == null || toView == null) return null

        val animatorSet = AnimatorSet()

        // Fade out current view
        val fadeOut = ObjectAnimator.ofFloat(
            fromView, "alpha",
            config.fadeMaxAlpha, config.fadeMinAlpha
        ).apply {
            duration = config.getEffectiveFadeOutDuration()
        }

        // Prepare and fade in new view
        toView.alpha = config.fadeMinAlpha
        toView.visibility = View.VISIBLE

        val fadeIn = ObjectAnimator.ofFloat(
            toView, "alpha",
            config.fadeMinAlpha, config.fadeMaxAlpha
        ).apply {
            duration = config.getEffectiveFadeInDuration()
            startDelay = config.getEffectiveFadeOutDuration() / 2 // Overlap slightly
        }

        animatorSet.playTogether(fadeOut, fadeIn)
        return animatorSet
    }

    /**
     * Creates a scale animation
     */
    private fun createScaleAnimation(fromView: View?, toView: View?): Animator? {
        if (fromView == null || toView == null) return null

        val animatorSet = AnimatorSet()

        // Scale down current view
        val scaleOutX = ObjectAnimator.ofFloat(fromView, "scaleX", 1f, config.scaleFromX)
        val scaleOutY = ObjectAnimator.ofFloat(fromView, "scaleY", 1f, config.scaleFromY)
        val fadeOut = ObjectAnimator.ofFloat(fromView, "alpha", 1f, 0f)

        // Prepare new view
        toView.scaleX = config.scaleFromX
        toView.scaleY = config.scaleFromY
        toView.alpha = 0f
        toView.visibility = View.VISIBLE

        // Scale up new view
        val scaleInX = ObjectAnimator.ofFloat(toView, "scaleX", config.scaleFromX, config.scaleToX)
        val scaleInY = ObjectAnimator.ofFloat(toView, "scaleY", config.scaleFromY, config.scaleToY)
        val fadeIn = ObjectAnimator.ofFloat(toView, "alpha", 0f, 1f)

        val scaleOut = AnimatorSet().apply {
            playTogether(scaleOutX, scaleOutY, fadeOut)
        }

        val scaleIn = AnimatorSet().apply {
            playTogether(scaleInX, scaleInY, fadeIn)
            startDelay = config.getEffectiveDuration() / 3
        }

        animatorSet.playTogether(scaleOut, scaleIn)
        return animatorSet
    }

    /**
     * Creates a flip animation
     */
    private fun createFlipAnimation(fromView: View?, toView: View?, direction: Int): Animator? {
        if (fromView == null || toView == null) return null

        val animatorSet = AnimatorSet()
        val isHorizontal = config.flipDirection == AnimationConfig.FlipDirection.HORIZONTAL

        // Set camera distance for 3D effect
        val cameraDistance = config.flipCameraDistance
        fromView.cameraDistance = cameraDistance
        toView.cameraDistance = cameraDistance

        // First half - rotate current view
        val firstHalf = if (isHorizontal) {
            ObjectAnimator.ofFloat(fromView, "rotationY", 0f, 90f * direction)
        } else {
            ObjectAnimator.ofFloat(fromView, "rotationX", 0f, 90f * direction)
        }.apply {
            duration = config.getEffectiveDuration() / 2
        }

        // Prepare new view
        toView.visibility = View.VISIBLE
        if (isHorizontal) {
            toView.rotationY = -90f * direction
        } else {
            toView.rotationX = -90f * direction
        }

        // Second half - rotate new view into place
        val secondHalf = if (isHorizontal) {
            ObjectAnimator.ofFloat(toView, "rotationY", -90f * direction, 0f)
        } else {
            ObjectAnimator.ofFloat(toView, "rotationX", -90f * direction, 0f)
        }.apply {
            duration = config.getEffectiveDuration() / 2
            startDelay = config.getEffectiveDuration() / 2
        }

        animatorSet.playTogether(firstHalf, secondHalf)
        return animatorSet
    }

    /**
     * Cancels the current animation if running
     */
    fun cancelCurrentAnimation() {
        currentAnimation?.cancel()
        currentAnimation = null
    }

    /**
     * Checks if an animation is currently running
     */
    fun isAnimating(): Boolean {
        return currentAnimation?.isRunning == true
    }

    /**
     * Adds a listener for animation events
     */
    fun addAnimationListener(listener: PageAnimationListener) {
        animationListeners.add(listener)
    }

    /**
     * Removes a listener for animation events
     */
    fun removeAnimationListener(listener: PageAnimationListener) {
        animationListeners.remove(listener)
    }

    /**
     * Clears all animation listeners
     */
    fun clearAnimationListeners() {
        animationListeners.clear()
    }

    /**
     * Updates the animation configuration
     */
    fun updateConfig(newConfig: AnimationConfig) {
        // If animation type changed and we're currently animating, restart with new config
        if (isAnimating() && newConfig.animationType != config.animationType) {
            cancelCurrentAnimation()
        }
    }
}