package com.sonpxp.pdfloader.model

import com.sonpxp.pdfloader.AnimationType

/**
 * Represents the current animation state of the PDF viewer
 * Contains information about ongoing animations and their progress
 */
data class AnimationState(
    /** Current animation state */
    val state: AnimationStateType = AnimationStateType.IDLE,

    /** Type of animation currently running */
    val animationType: AnimationType = AnimationType.NONE,

    /** Animation progress (0.0 to 1.0) */
    val progress: Float = 0f,

    /** Animation duration in milliseconds */
    val duration: Long = 0,

    /** Animation start timestamp */
    val startTime: Long = 0,

    /** Animation end timestamp (0 if not finished) */
    val endTime: Long = 0,

    /** Elapsed time since animation started */
    val elapsedTime: Long = if (startTime > 0) System.currentTimeMillis() - startTime else 0,

    /** Remaining time until animation completes */
    val remainingTime: Long = if (duration > 0) (duration - elapsedTime).coerceAtLeast(0) else 0,

    /** Animation start values */
    val startValues: Map<String, Any> = emptyMap(),

    /** Animation end values */
    val endValues: Map<String, Any> = emptyMap(),

    /** Current interpolated values */
    val currentValues: Map<String, Any> = emptyMap(),

    /** Whether animation can be interrupted */
    val interruptible: Boolean = true,

    /** Animation priority level */
    val priority: AnimationPriority = AnimationPriority.NORMAL,

    /** Additional animation properties */
    val properties: Map<String, Any> = emptyMap()
) {

    /**
     * Animation state types
     */
    enum class AnimationStateType {
        /** No animation running */
        IDLE,

        /** Animation is running */
        RUNNING,

        /** Animation is paused */
        PAUSED,

        /** Animation completed successfully */
        COMPLETED,

        /** Animation was cancelled */
        CANCELLED,

        /** Animation failed with error */
        FAILED
    }

    /**
     * Animation priority levels
     */
    enum class AnimationPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    /**
     * Checks if animation is currently active
     */
    fun isActive(): Boolean {
        return state == AnimationStateType.RUNNING
    }

    /**
     * Checks if animation is complete (finished or cancelled)
     */
    fun isComplete(): Boolean {
        return state == AnimationStateType.COMPLETED ||
                state == AnimationStateType.CANCELLED ||
                state == AnimationStateType.FAILED
    }

    /**
     * Checks if animation can be interrupted
     */
    fun canBeInterrupted(): Boolean {
        return interruptible && isActive()
    }

    /**
     * Gets the animation completion percentage (0 to 100)
     */
    fun getCompletionPercentage(): Int {
        return (progress * 100).toInt()
    }

    /**
     * Gets the current interpolated value for a specific property
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getCurrentValue(key: String): T? {
        return currentValues[key] as? T
    }

    /**
     * Gets the start value for a specific property
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getStartValue(key: String): T? {
        return startValues[key] as? T
    }

    /**
     * Gets the end value for a specific property
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getEndValue(key: String): T? {
        return endValues[key] as? T
    }

    /**
     * Calculates the expected completion time
     */
    fun getExpectedCompletionTime(): Long {
        return if (isActive() && duration > 0) {
            startTime + duration
        } else 0
    }

    /**
     * Gets the animation speed (progress per millisecond)
     */
    fun getAnimationSpeed(): Float {
        return if (duration > 0) 1f / duration else 0f
    }

    /**
     * Creates a copy with updated progress
     */
    fun withProgress(newProgress: Float, newCurrentValues: Map<String, Any> = currentValues): AnimationState {
        val clampedProgress = newProgress.coerceIn(0f, 1f)
        val newState = if (clampedProgress >= 1f) AnimationStateType.COMPLETED else state
        val newEndTime = if (newState == AnimationStateType.COMPLETED && endTime == 0L) {
            System.currentTimeMillis()
        } else endTime

        return copy(
            progress = clampedProgress,
            state = newState,
            currentValues = newCurrentValues,
            endTime = newEndTime,
            elapsedTime = if (startTime > 0) System.currentTimeMillis() - startTime else 0
        )
    }

    /**
     * Creates a copy with updated state
     */
    fun withState(newState: AnimationStateType): AnimationState {
        val newEndTime = when (newState) {
            AnimationStateType.COMPLETED,
            AnimationStateType.CANCELLED,
            AnimationStateType.FAILED -> {
                if (endTime == 0L) System.currentTimeMillis() else endTime
            }
            else -> endTime
        }

        return copy(
            state = newState,
            endTime = newEndTime,
            elapsedTime = if (startTime > 0) System.currentTimeMillis() - startTime else 0
        )
    }

    /**
     * Creates a copy with updated current values
     */
    fun withCurrentValues(newCurrentValues: Map<String, Any>): AnimationState {
        return copy(currentValues = newCurrentValues)
    }

    /**
     * Creates a paused copy of the animation
     */
    fun pause(): AnimationState {
        return copy(state = AnimationStateType.PAUSED)
    }

    /**
     * Creates a resumed copy of the animation
     */
    fun resume(): AnimationState {
        return if (state == AnimationStateType.PAUSED) {
            copy(state = AnimationStateType.RUNNING)
        } else this
    }

    /**
     * Creates a cancelled copy of the animation
     */
    fun cancel(): AnimationState {
        return copy(
            state = AnimationStateType.CANCELLED,
            endTime = if (endTime == 0L) System.currentTimeMillis() else endTime
        )
    }

    /**
     * Creates a completed copy of the animation
     */
    fun complete(): AnimationState {
        return copy(
            state = AnimationStateType.COMPLETED,
            progress = 1f,
            endTime = if (endTime == 0L) System.currentTimeMillis() else endTime,
            currentValues = endValues
        )
    }

    /**
     * Creates a failed copy of the animation
     */
    fun fail(): AnimationState {
        return copy(
            state = AnimationStateType.FAILED,
            endTime = if (endTime == 0L) System.currentTimeMillis() else endTime
        )
    }

    /**
     * Gets the actual elapsed time for the animation
     */
    fun getActualElapsedTime(): Long {
        return when {
            startTime == 0L -> 0L
            endTime > 0L -> endTime - startTime
            else -> System.currentTimeMillis() - startTime
        }
    }

    /**
     * Gets the animation efficiency (how close to expected duration)
     */
    fun getEfficiency(): Float {
        val actualTime = getActualElapsedTime()
        return if (duration > 0 && actualTime > 0) {
            duration.toFloat() / actualTime.toFloat()
        } else 1f
    }

    /**
     * Gets animation summary for debugging
     */
    fun getSummary(): String {
        return "${animationType.name} animation: $state (${getCompletionPercentage()}%)"
    }

    /**
     * Gets detailed animation information for debugging
     */
    fun getDetailedInfo(): String {
        return buildString {
            appendLine("Animation State:")
            appendLine("  Type: $animationType")
            appendLine("  State: $state")
            appendLine("  Progress: ${String.format("%.1f", progress * 100)}%")
            appendLine("  Duration: ${duration}ms")
            appendLine("  Elapsed: ${getActualElapsedTime()}ms")
            appendLine("  Remaining: ${remainingTime}ms")
            appendLine("  Priority: $priority")
            appendLine("  Interruptible: $interruptible")

            if (startTime > 0) {
                appendLine("  Started: ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date(startTime))}")
            }

            if (endTime > 0) {
                appendLine("  Ended: ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date(endTime))}")
                appendLine("  Efficiency: ${String.format("%.2f", getEfficiency())}")
            }

            if (startValues.isNotEmpty()) {
                appendLine("  Start Values:")
                startValues.forEach { (key, value) ->
                    appendLine("    $key: $value")
                }
            }

            if (endValues.isNotEmpty()) {
                appendLine("  End Values:")
                endValues.forEach { (key, value) ->
                    appendLine("    $key: $value")
                }
            }

            if (currentValues.isNotEmpty()) {
                appendLine("  Current Values:")
                currentValues.forEach { (key, value) ->
                    appendLine("    $key: $value")
                }
            }

            if (properties.isNotEmpty()) {
                appendLine("  Properties:")
                properties.forEach { (key, value) ->
                    appendLine("    $key: $value")
                }
            }
        }
    }

    companion object {
        /**
         * Creates an idle animation state
         */
        fun idle(): AnimationState {
            return AnimationState()
        }

        /**
         * Creates a new running animation state
         */
        fun start(
            animationType: AnimationType,
            duration: Long,
            startValues: Map<String, Any> = emptyMap(),
            endValues: Map<String, Any> = emptyMap(),
            priority: AnimationPriority = AnimationPriority.NORMAL,
            interruptible: Boolean = true
        ): AnimationState {
            val currentTime = System.currentTimeMillis()

            return AnimationState(
                state = AnimationStateType.RUNNING,
                animationType = animationType,
                duration = duration,
                startTime = currentTime,
                startValues = startValues,
                endValues = endValues,
                currentValues = startValues,
                priority = priority,
                interruptible = interruptible
            )
        }

        /**
         * Creates an animation state for page transitions
         */
        fun pageTransition(
            fromPage: Int,
            toPage: Int,
            animationType: AnimationType,
            duration: Long
        ): AnimationState {
            return start(
                animationType = animationType,
                duration = duration,
                startValues = mapOf("page" to fromPage),
                endValues = mapOf("page" to toPage),
                priority = AnimationPriority.HIGH,
                interruptible = false
            )
        }

        /**
         * Creates an animation state for zoom transitions
         */
        fun zoomTransition(
            fromZoom: Float,
            toZoom: Float,
            focusX: Float,
            focusY: Float,
            duration: Long
        ): AnimationState {
            return start(
                animationType = AnimationType.SCALE,
                duration = duration,
                startValues = mapOf(
                    "zoom" to fromZoom,
                    "focusX" to focusX,
                    "focusY" to focusY
                ),
                endValues = mapOf(
                    "zoom" to toZoom,
                    "focusX" to focusX,
                    "focusY" to focusY
                ),
                priority = AnimationPriority.HIGH
            )
        }

        /**
         * Creates an animation state for scroll transitions
         */
        fun scrollTransition(
            fromX: Float,
            fromY: Float,
            toX: Float,
            toY: Float,
            duration: Long
        ): AnimationState {
            return start(
                animationType = AnimationType.SLIDE,
                duration = duration,
                startValues = mapOf(
                    "scrollX" to fromX,
                    "scrollY" to fromY
                ),
                endValues = mapOf(
                    "scrollX" to toX,
                    "scrollY" to toY
                ),
                priority = AnimationPriority.NORMAL
            )
        }

        /**
         * Creates an animation state for fade transitions
         */
        fun fadeTransition(
            fromAlpha: Float,
            toAlpha: Float,
            duration: Long
        ): AnimationState {
            return start(
                animationType = AnimationType.FADE,
                duration = duration,
                startValues = mapOf("alpha" to fromAlpha),
                endValues = mapOf("alpha" to toAlpha),
                priority = AnimationPriority.NORMAL
            )
        }
    }
}