package com.sonpxp.pdfloader.model


import android.graphics.PointF

/**
 * Represents the current zoom state of the PDF viewer
 * Contains information about zoom level, focus point, and zoom constraints
 */
data class ZoomState(
    /** Current zoom level (1.0 = 100%) */
    val zoomLevel: Float = 1.0f,

    /** Previous zoom level (for delta calculations) */
    val previousZoomLevel: Float = 1.0f,

    /** Minimum allowed zoom level */
    val minZoom: Float = 0.5f,

    /** Maximum allowed zoom level */
    val maxZoom: Float = 5.0f,

    /** Current zoom focus point (center of zoom operation) */
    val focusPoint: PointF = PointF(0f, 0f),

    /** Current zoom state */
    val state: ZoomState = ZoomState.IDLE,

    /** Whether zoom is currently being animated */
    val isAnimatingZoom: Boolean = false,

    /** Whether user is currently pinch-zooming */
    val isUserZooming: Boolean = false,

    /** Current zoom fit mode */
    val fitMode: ZoomFitMode = ZoomFitMode.FIT_WIDTH,

    /** Whether zoom is at minimum level */
    val isAtMinZoom: Boolean = false,

    /** Whether zoom is at maximum level */
    val isAtMaxZoom: Boolean = false,

    /** Double-tap zoom scale */
    val doubleTapZoomScale: Float = 2.0f,

    /** Whether zoom should be centered */
    val centerZoom: Boolean = false,

    /** Last zoom update timestamp */
    val lastUpdateTime: Long = System.currentTimeMillis(),

    /** Zoom velocity (for momentum zoom) */
    val zoomVelocity: Float = 0f,

    /** Page dimensions at current zoom */
    val pageWidth: Float = 0f,
    val pageHeight: Float = 0f,

    /** View dimensions */
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,

    /** Additional zoom properties */
    val properties: Map<String, Any> = emptyMap()
) {

    /**
     * Gets the zoom percentage (100% = normal size)
     */
    fun getZoomPercentage(): Int {
        return (zoomLevel * 100).toInt()
    }

    /**
     * Gets the zoom delta since last update
     */
    fun getZoomDelta(): Float {
        return zoomLevel - previousZoomLevel
    }

    /**
     * Checks if zoom level has changed significantly
     */
    fun hasZoomChanged(threshold: Float = 0.01f): Boolean {
        return kotlin.math.abs(getZoomDelta()) > threshold
    }

    /**
     * Gets the constrained zoom level (within min/max bounds)
     */
    fun getConstrainedZoomLevel(): Float {
        return zoomLevel.coerceIn(minZoom, maxZoom)
    }

    /**
     * Checks if the zoom level is within bounds
     */
    fun isWithinBounds(): Boolean {
        return zoomLevel >= minZoom && zoomLevel <= maxZoom
    }

    /**
     * Gets the scale factor relative to fit-width zoom
     */
    fun getScaleFactorToFitWidth(): Float {
        return if (pageWidth > 0 && viewWidth > 0) {
            (viewWidth / pageWidth) * zoomLevel
        } else zoomLevel
    }

    /**
     * Gets the scale factor relative to fit-height zoom
     */
    fun getScaleFactorToFitHeight(): Float {
        return if (pageHeight > 0 && viewHeight > 0) {
            (viewHeight / pageHeight) * zoomLevel
        } else zoomLevel
    }

    /**
     * Calculates the zoom level needed to fit width
     */
    fun calculateFitWidthZoom(): Float {
        return if (pageWidth > 0 && viewWidth > 0) {
            viewWidth / pageWidth
        } else 1.0f
    }

    /**
     * Calculates the zoom level needed to fit height
     */
    fun calculateFitHeightZoom(): Float {
        return if (pageHeight > 0 && viewHeight > 0) {
            viewHeight / pageHeight
        } else 1.0f
    }

    /**
     * Calculates the zoom level needed to fit the entire page
     */
    fun calculateFitPageZoom(): Float {
        val fitWidthZoom = calculateFitWidthZoom()
        val fitHeightZoom = calculateFitHeightZoom()
        return kotlin.math.min(fitWidthZoom, fitHeightZoom)
    }

    /**
     * Calculates the zoom level needed to fill the view (may crop content)
     */
    fun calculateFillViewZoom(): Float {
        val fitWidthZoom = calculateFitWidthZoom()
        val fitHeightZoom = calculateFitHeightZoom()
        return kotlin.math.max(fitWidthZoom, fitHeightZoom)
    }

    /**
     * Gets the zoom level for a specific fit mode
     */
    fun getZoomForFitMode(mode: ZoomFitMode): Float {
        return when (mode) {
            ZoomFitMode.FIT_WIDTH -> calculateFitWidthZoom()
            ZoomFitMode.FIT_HEIGHT -> calculateFitHeightZoom()
            ZoomFitMode.FIT_PAGE -> calculateFitPageZoom()
            ZoomFitMode.FILL_VIEW -> calculateFillViewZoom()
            ZoomFitMode.ORIGINAL_SIZE -> 1.0f
        }.coerceIn(minZoom, maxZoom)
    }

    /**
     * Gets the next zoom level for double-tap zoom
     */
    fun getNextDoubleTapZoom(): Float {
        val fitWidthZoom = calculateFitWidthZoom()
        val targetZoom = doubleTapZoomScale

        return when {
            // If at fit-width, go to double-tap zoom
            kotlin.math.abs(zoomLevel - fitWidthZoom) < 0.1f -> targetZoom.coerceIn(minZoom, maxZoom)
            // If at double-tap zoom or higher, go back to fit-width
            zoomLevel >= targetZoom -> fitWidthZoom.coerceIn(minZoom, maxZoom)
            // Otherwise, go to double-tap zoom
            else -> targetZoom.coerceIn(minZoom, maxZoom)
        }
    }

    /**
     * Checks if zoom can be increased
     */
    fun canZoomIn(): Boolean {
        return zoomLevel < maxZoom - 0.01f
    }

    /**
     * Checks if zoom can be decreased
     */
    fun canZoomOut(): Boolean {
        return zoomLevel > minZoom + 0.01f
    }

    /**
     * Gets the zoom range (max - min)
     */
    fun getZoomRange(): Float {
        return maxZoom - minZoom
    }

    /**
     * Gets the zoom progress within the range (0.0 to 1.0)
     */
    fun getZoomProgress(): Float {
        val range = getZoomRange()
        return if (range > 0) {
            ((zoomLevel - minZoom) / range).coerceIn(0f, 1f)
        } else 0f
    }

    /**
     * Creates a copy with updated zoom level
     */
    fun withZoomLevel(newZoomLevel: Float, newFocusPoint: PointF? = null): ZoomState {
        return copy(
            previousZoomLevel = zoomLevel,
            zoomLevel = newZoomLevel,
            focusPoint = newFocusPoint ?: focusPoint,
            lastUpdateTime = System.currentTimeMillis()
        ).updateBoundaryStates()
    }

    /**
     * Creates a copy with updated zoom bounds
     */
    fun withZoomBounds(newMinZoom: Float, newMaxZoom: Float): ZoomState {
        return copy(
            minZoom = newMinZoom,
            maxZoom = newMaxZoom
        ).updateBoundaryStates()
    }

    /**
     * Creates a copy with updated focus point
     */
    fun withFocusPoint(newFocusPoint: PointF): ZoomState {
        return copy(focusPoint = PointF(newFocusPoint.x, newFocusPoint.y))
    }

    /**
     * Creates a copy with updated zoom state
     */
    fun withState(newState: ZoomState): ZoomState {
        return copy(state = newState)
    }

    /**
     * Creates a copy with updated animation state
     */
    fun withAnimationState(animating: Boolean): ZoomState {
        return copy(isAnimatingZoom = animating)
    }

    /**
     * Creates a copy with updated user zoom state
     */
    fun withUserZoomState(zooming: Boolean): ZoomState {
        return copy(isUserZooming = zooming)
    }

    /**
     * Creates a copy with updated fit mode
     */
    fun withFitMode(newFitMode: ZoomFitMode): ZoomState {
        return copy(fitMode = newFitMode)
    }

    /**
     * Creates a copy with updated page dimensions
     */
    fun withPageDimensions(width: Float, height: Float): ZoomState {
        return copy(pageWidth = width, pageHeight = height)
    }

    /**
     * Creates a copy with updated view dimensions
     */
    fun withViewDimensions(width: Float, height: Float): ZoomState {
        return copy(viewWidth = width, viewHeight = height)
    }

    /**
     * Creates a copy with updated zoom velocity
     */
    fun withZoomVelocity(velocity: Float): ZoomState {
        return copy(zoomVelocity = velocity)
    }

    /**
     * Updates boundary states based on current zoom level
     */
    private fun updateBoundaryStates(): ZoomState {
        val tolerance = 0.01f

        return copy(
            isAtMinZoom = zoomLevel <= minZoom + tolerance,
            isAtMaxZoom = zoomLevel >= maxZoom - tolerance
        )
    }

    /**
     * Gets the zoom position that would result from applying current velocity for a given time
     */
    fun predictZoomLevel(timeMs: Long): Float {
        if (kotlin.math.abs(zoomVelocity) < 0.01f) return zoomLevel

        val timeSeconds = timeMs / 1000f
        val friction = 0.9f // Friction factor for velocity decay

        val predictedVelocity = zoomVelocity * kotlin.math.pow(friction, timeSeconds.toDouble()).toFloat()
        return (zoomLevel + predictedVelocity * timeSeconds).coerceIn(minZoom, maxZoom)
    }

    /**
     * Gets zoom state summary for debugging
     */
    fun getSummary(): String {
        return "Zoom: ${getZoomPercentage()}% (${String.format("%.2f", zoomLevel)}), " +
                "Range: ${String.format("%.2f", minZoom)}-${String.format("%.2f", maxZoom)}, " +
                "State: $state"
    }

    /**
     * Gets detailed zoom information for debugging
     */
    fun getDetailedInfo(): String {
        return buildString {
            appendLine("Zoom State:")
            appendLine("  Level: ${String.format("%.3f", zoomLevel)} (${getZoomPercentage()}%)")
            appendLine("  Previous: ${String.format("%.3f", previousZoomLevel)}")
            appendLine("  Delta: ${String.format("%.3f", getZoomDelta())}")
            appendLine("  Range: ${String.format("%.2f", minZoom)} - ${String.format("%.2f", maxZoom)}")
            appendLine("  Progress: ${String.format("%.1f", getZoomProgress() * 100)}%")
            appendLine("  Focus Point: (${String.format("%.1f", focusPoint.x)}, ${String.format("%.1f", focusPoint.y)})")
            appendLine("  State: $state")
            appendLine("  Fit Mode: $fitMode")
            appendLine("  Animating: $isAnimatingZoom")
            appendLine("  User Zooming: $isUserZooming")
            appendLine("  Center Zoom: $centerZoom")
            appendLine("  Double-tap Scale: ${String.format("%.2f", doubleTapZoomScale)}")
            appendLine("  Velocity: ${String.format("%.3f", zoomVelocity)}")
            appendLine("  Page Size: ${String.format("%.1f", pageWidth)} x ${String.format("%.1f", pageHeight)}")
            appendLine("  View Size: ${String.format("%.1f", viewWidth)} x ${String.format("%.1f", viewHeight)}")
            appendLine("  At Min: $isAtMinZoom, At Max: $isAtMaxZoom")
            appendLine("  Can Zoom In: ${canZoomIn()}, Can Zoom Out: ${canZoomOut()}")

            // Calculated zoom levels for different fit modes
            appendLine("  Fit Zooms:")
            appendLine("    Width: ${String.format("%.3f", calculateFitWidthZoom())}")
            appendLine("    Height: ${String.format("%.3f", calculateFitHeightZoom())}")
            appendLine("    Page: ${String.format("%.3f", calculateFitPageZoom())}")
            appendLine("    Fill: ${String.format("%.3f", calculateFillViewZoom())}")
            appendLine("    Next Double-tap: ${String.format("%.3f", getNextDoubleTapZoom())}")

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
         * Creates an initial zoom state
         */
        fun initial(
            minZoom: Float = 0.5f,
            maxZoom: Float = 5.0f,
            doubleTapZoomScale: Float = 2.0f
        ): ZoomState {
            return ZoomState(
                minZoom = minZoom,
                maxZoom = maxZoom,
                doubleTapZoomScale = doubleTapZoomScale
            ).updateBoundaryStates()
        }

        /**
         * Creates a zoom state for specific page and view dimensions
         */
        fun forDimensions(
            pageWidth: Float,
            pageHeight: Float,
            viewWidth: Float,
            viewHeight: Float,
            fitMode: ZoomFitMode = ZoomFitMode.FIT_WIDTH,
            minZoom: Float = 0.5f,
            maxZoom: Float = 5.0f
        ): ZoomState {
            val state = ZoomState(
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                fitMode = fitMode,
                minZoom = minZoom,
                maxZoom = maxZoom,
                focusPoint = PointF(viewWidth / 2f, viewHeight / 2f)
            )

            val initialZoom = state.getZoomForFitMode(fitMode)
            return state.withZoomLevel(initialZoom)
        }

        /**
         * Creates a zoom state with specific zoom level and focus point
         */
        fun withZoom(
            zoomLevel: Float,
            focusPoint: PointF,
            minZoom: Float = 0.5f,
            maxZoom: Float = 5.0f
        ): ZoomState {
            return ZoomState(
                zoomLevel = zoomLevel,
                focusPoint = PointF(focusPoint.x, focusPoint.y),
                minZoom = minZoom,
                maxZoom = maxZoom
            ).updateBoundaryStates()
        }
    }
}