package com.sonpxp.pdfloader.animation


import android.view.animation.Interpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.BounceInterpolator
import com.sonpxp.pdfloader.AnimationType

/**
 * Configuration class for animations in the PDF viewer
 * Contains settings and parameters for various animation types
 */
data class AnimationConfig(
    /** Animation type to use */
    val animationType: AnimationType = AnimationType.NONE,

    /** Duration of animations in milliseconds */
    val duration: Long = 400,

    /** Interpolator for animations */
    val interpolator: Interpolator = AccelerateDecelerateInterpolator(),

    /** Whether animations are enabled globally */
    val enabled: Boolean = true,

    /** Minimum duration for any animation */
    val minDuration: Long = 100,

    /** Maximum duration for any animation */
    val maxDuration: Long = 1000,

    /** Scale factor for animation speed (1.0 = normal, 0.5 = half speed, 2.0 = double speed) */
    val speedMultiplier: Float = 1.0f,

    /** Whether to animate page transitions */
    val animatePageTransitions: Boolean = true,

    /** Whether to animate zoom changes */
    val animateZoom: Boolean = true,

    /** Whether to animate scroll movements */
    val animateScroll: Boolean = true,

    /** Alpha animation settings */
    val fadeInDuration: Long = 200,
    val fadeOutDuration: Long = 200,
    val fadeMinAlpha: Float = 0.0f,
    val fadeMaxAlpha: Float = 1.0f,

    /** Scale animation settings */
    val scaleFromX: Float = 0.8f,
    val scaleFromY: Float = 0.8f,
    val scaleToX: Float = 1.0f,
    val scaleToY: Float = 1.0f,
    val scalePivotX: Float = 0.5f,
    val scalePivotY: Float = 0.5f,

    /** Slide animation settings */
    val slideDistance: Float = 300f, // Distance in pixels
    val slideFromLeft: Boolean = true,
    val slideFromTop: Boolean = false,

    /** Flip animation settings */
    val flipDirection: FlipDirection = FlipDirection.HORIZONTAL,
    val flipCameraDistance: Float = 8000f
) {

    enum class FlipDirection {
        HORIZONTAL,
        VERTICAL
    }

    /**
     * Gets the effective duration considering speed multiplier and bounds
     */
    fun getEffectiveDuration(): Long {
        val adjustedDuration = (duration / speedMultiplier).toLong()
        return adjustedDuration.coerceIn(minDuration, maxDuration)
    }

    /**
     * Gets the effective fade in duration
     */
    fun getEffectiveFadeInDuration(): Long {
        val adjustedDuration = (fadeInDuration / speedMultiplier).toLong()
        return adjustedDuration.coerceIn(minDuration, maxDuration)
    }

    /**
     * Gets the effective fade out duration
     */
    fun getEffectiveFadeOutDuration(): Long {
        val adjustedDuration = (fadeOutDuration / speedMultiplier).toLong()
        return adjustedDuration.coerceIn(minDuration, maxDuration)
    }

    /**
     * Creates a copy with different animation type
     */
    fun withAnimationType(type: AnimationType): AnimationConfig {
        return copy(animationType = type)
    }

    /**
     * Creates a copy with different duration
     */
    fun withDuration(newDuration: Long): AnimationConfig {
        return copy(duration = newDuration)
    }

    /**
     * Creates a copy with different interpolator
     */
    fun withInterpolator(newInterpolator: Interpolator): AnimationConfig {
        return copy(interpolator = newInterpolator)
    }

    /**
     * Creates a copy optimized for fast animations
     */
    fun optimizeForSpeed(): AnimationConfig {
        return copy(
            duration = 200,
            fadeInDuration = 100,
            fadeOutDuration = 100,
            interpolator = LinearInterpolator(),
            speedMultiplier = 2.0f
        )
    }

    /**
     * Creates a copy optimized for smooth animations
     */
    fun optimizeForSmoothness(): AnimationConfig {
        return copy(
            duration = 600,
            fadeInDuration = 300,
            fadeOutDuration = 300,
            interpolator = AccelerateDecelerateInterpolator(),
            speedMultiplier = 0.8f
        )
    }

    /**
     * Creates a copy with bounce effect
     */
    fun withBounceEffect(): AnimationConfig {
        return copy(
            interpolator = BounceInterpolator(),
            duration = 800
        )
    }

    /**
     * Creates a copy with overshoot effect
     */
    fun withOvershootEffect(): AnimationConfig {
        return copy(
            interpolator = OvershootInterpolator(1.5f),
            duration = 500
        )
    }

    companion object {
        /**
         * Default configuration for page transitions
         */
        @JvmStatic
        fun defaultPageTransition(): AnimationConfig {
            return AnimationConfig(
                animationType = AnimationType.SLIDE,
                duration = 400,
                interpolator = AccelerateDecelerateInterpolator()
            )
        }

        /**
         * Default configuration for zoom animations
         */
        @JvmStatic
        fun defaultZoomAnimation(): AnimationConfig {
            return AnimationConfig(
                animationType = AnimationType.SCALE,
                duration = 300,
                interpolator = AccelerateDecelerateInterpolator()
            )
        }

        /**
         * Configuration for no animations (immediate changes)
         */
        @JvmStatic
        fun noAnimation(): AnimationConfig {
            return AnimationConfig(
                animationType = AnimationType.NONE,
                enabled = false,
                duration = 0
            )
        }

        /**
         * Fast animation configuration for low-end devices
         */
        @JvmStatic
        fun fastAnimation(): AnimationConfig {
            return AnimationConfig().optimizeForSpeed()
        }

        /**
         * Smooth animation configuration for high-end devices
         */
        @JvmStatic
        fun smoothAnimation(): AnimationConfig {
            return AnimationConfig().optimizeForSmoothness()
        }
    }
}