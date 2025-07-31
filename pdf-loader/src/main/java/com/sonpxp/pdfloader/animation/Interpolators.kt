package com.sonpxp.pdfloader.animation

import android.view.animation.BaseInterpolator
import kotlin.math.*

/**
 * Collection of custom interpolators for PDF viewer animations
 */
object Interpolators {

    /**
     * Smooth ease-in-out interpolator with customizable curve
     */
    class SmoothInterpolator(private val smoothness: Float = 2.0f) : BaseInterpolator() {
        override fun getInterpolation(input: Float): Float {
            return (1 - cos(input * PI)).toFloat() / 2f
        }
    }

    /**
     * Elastic interpolator that simulates elastic behavior
     */
    class ElasticInterpolator(
        private val amplitude: Float = 1.0f,
        private val period: Float = 0.3f
    ) : BaseInterpolator() {

        override fun getInterpolation(input: Float): Float {
            if (input == 0f || input == 1f) return input

            val p = period
            val a = amplitude
            val s = p / (2 * PI) * asin(1 / a)

            return (a * 2.0.pow(-10.0 * input) *
                    sin((input - s) * (2 * PI) / p) + 1).toFloat()
        }
    }

    /**
     * Back interpolator that goes slightly beyond the target before settling
     */
    class BackInterpolator(private val overshoot: Float = 1.70158f) : BaseInterpolator() {
        override fun getInterpolation(input: Float): Float {
            return input * input * ((overshoot + 1) * input - overshoot)
        }
    }

    /**
     * Anticipate interpolator that starts backwards before going forward
     */
    class AnticipateInterpolator(private val tension: Float = 2.0f) : BaseInterpolator() {
        override fun getInterpolation(input: Float): Float {
            return input * input * ((tension + 1) * input - tension)
        }
    }

    /**
     * Custom cubic bezier interpolator
     */
    class CubicBezierInterpolator(
        private val x1: Float,
        private val y1: Float,
        private val x2: Float,
        private val y2: Float
    ) : BaseInterpolator() {

        override fun getInterpolation(input: Float): Float {
            return calculateBezier(getTForX(input), y1, y2)
        }

        private fun calculateBezier(t: Float, a1: Float, a2: Float): Float {
            return ((1 - 3 * a2 + 3 * a1) * t * t * t +
                    (3 * a2 - 6 * a1) * t * t +
                    3 * a1 * t)
        }

        private fun getTForX(x: Float): Float {
            var t = x
            for (i in 0 until 8) {
                val currentX = calculateBezier(t, x1, x2) - x
                if (abs(currentX) < 0.001f) break

                val currentSlope = getSlope(t, x1, x2)
                if (abs(currentSlope) < 0.001f) break

                t -= currentX / currentSlope
            }
            return t
        }

        private fun getSlope(t: Float, a1: Float, a2: Float): Float {
            return 3 * (1 - 3 * a2 + 3 * a1) * t * t +
                    2 * (3 * a2 - 6 * a1) * t +
                    3 * a1
        }
    }

    /**
     * Pulse interpolator that creates a pulsing effect
     */
    class PulseInterpolator(private val pulses: Int = 3) : BaseInterpolator() {
        override fun getInterpolation(input: Float): Float {
            return abs(sin(input * PI * pulses)).toFloat()
        }
    }

    /**
     * Wave interpolator that creates a wave-like motion
     */
    class WaveInterpolator(
        private val frequency: Float = 2.0f,
        private val amplitude: Float = 0.1f
    ) : BaseInterpolator() {
        override fun getInterpolation(input: Float): Float {
            return input + amplitude * sin(input * PI * frequency).toFloat()
        }
    }

    /**
     * Spring interpolator that simulates spring physics
     */
    class SpringInterpolator(
        private val tension: Float = 300f,
        private val friction: Float = 20f
    ) : BaseInterpolator() {

        override fun getInterpolation(input: Float): Float {
            val dampingRatio = friction / (2 * sqrt(tension))
            val angularFreq = sqrt(tension)

            return if (dampingRatio < 1) {
                // Under-damped
                val envelope = exp(-dampingRatio * angularFreq * input)
                val oscillation = cos(angularFreq * sqrt(1 - dampingRatio * dampingRatio) * input)
                (1 - envelope * oscillation).toFloat()
            } else {
                // Over-damped or critically damped
                (1 - exp(-angularFreq * input)).toFloat()
            }
        }
    }

    /**
     * Exponential interpolator for smooth acceleration/deceleration
     */
    class ExponentialInterpolator(
        private val exponent: Float = 2.0f,
        private val direction: Direction = Direction.EASE_IN_OUT
    ) : BaseInterpolator() {

        enum class Direction {
            EASE_IN,
            EASE_OUT,
            EASE_IN_OUT
        }

        override fun getInterpolation(input: Float): Float {
            return when (direction) {
                Direction.EASE_IN -> input.pow(exponent)
                Direction.EASE_OUT -> 1 - (1 - input).pow(exponent)
                Direction.EASE_IN_OUT -> {
                    if (input < 0.5f) {
                        0.5f * (2 * input).pow(exponent)
                    } else {
                        1 - 0.5f * (2 * (1 - input)).pow(exponent)
                    }
                }
            }
        }
    }

    /**
     * Bounce interpolator that simulates ball bouncing
     */
    class AdvancedBounceInterpolator(
        private val bounces: Int = 3,
        private val bounciness: Float = 0.6f
    ) : BaseInterpolator() {

        override fun getInterpolation(input: Float): Float {
            if (input >= 1.0f) return 1.0f

            var value = input
            var bounceValue = 1.0f

            for (i in 0 until bounces) {
                val bouncePoint = (bounces - i) / bounces.toFloat()
                if (value >= bouncePoint) {
                    val bounceInput = (value - bouncePoint) / (1 - bouncePoint)
                    val bounceHeight = bounciness.pow(i)
                    return 1 - bounceHeight * (1 - bounceInput * bounceInput)
                }
                bounceValue *= bounciness
            }

            return value
        }
    }

    // Predefined common interpolators
    val SMOOTH = SmoothInterpolator()
    val ELASTIC = ElasticInterpolator()
    val BACK = BackInterpolator()
    val ANTICIPATE = AnticipateInterpolator()
    val SPRING = SpringInterpolator()
    val PULSE = PulseInterpolator()
    val WAVE = WaveInterpolator()
    val BOUNCE_ADVANCED = AdvancedBounceInterpolator()

    // Material Design interpolators
    val MATERIAL_FAST_OUT_SLOW_IN = CubicBezierInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
    val MATERIAL_LINEAR_OUT_SLOW_IN = CubicBezierInterpolator(0.0f, 0.0f, 0.2f, 1.0f)
    val MATERIAL_FAST_OUT_LINEAR_IN = CubicBezierInterpolator(0.4f, 0.0f, 1.0f, 1.0f)
    val MATERIAL_STANDARD = CubicBezierInterpolator(0.4f, 0.0f, 0.2f, 1.0f)

    // iOS-like interpolators
    val IOS_EASE_IN_OUT = CubicBezierInterpolator(0.42f, 0.0f, 0.58f, 1.0f)
    val IOS_EASE_IN = CubicBezierInterpolator(0.42f, 0.0f, 1.0f, 1.0f)
    val IOS_EASE_OUT = CubicBezierInterpolator(0.0f, 0.0f, 0.58f, 1.0f)
}