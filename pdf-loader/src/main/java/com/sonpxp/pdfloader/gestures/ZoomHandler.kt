package com.sonpxp.pdfloader.gestures

import android.graphics.PointF
import com.sonpxp.pdfloader.model.Configuration
import com.sonpxp.pdfloader.utils.MathUtils
import kotlin.math.*

/**
 * Handles zoom operations and transformations
 * Manages zoom levels, center points, and zoom constraints
 */
class ZoomHandler(
    private var configuration: Configuration
) {

    // Current zoom state
    private var currentZoom = 1f
    private var centerX = 0f
    private var centerY = 0f

    // Viewport dimensions
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var contentWidth = 0f
    private var contentHeight = 0f

    // Zoom constraints
    private var minZoom = 0.1f
    private var maxZoom = 10f

    // Animation state
    private var isAnimating = false
    private var animationStartTime = 0L
    private var animationDuration = 300L
    private var animationStartZoom = 1f
    private var animationTargetZoom = 1f
    private var animationStartCenter = PointF()
    private var animationTargetCenter = PointF()

    data class ZoomResult(
        val newZoom: Float,
        val newCenterX: Float,
        val newCenterY: Float,
        val zoomChanged: Boolean,
        val centerChanged: Boolean
    )

    init {
        updateZoomConstraints()
    }

    /**
     * Sets viewport and content dimensions
     */
    fun setDimensions(
        viewportWidth: Float,
        viewportHeight: Float,
        contentWidth: Float,
        contentHeight: Float
    ) {
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        this.contentWidth = contentWidth
        this.contentHeight = contentHeight

        updateZoomConstraints()
    }

    /**
     * Handles pinch zoom gesture
     */
    fun handlePinchZoom(scaleFactor: Float, focusX: Float, focusY: Float): ZoomResult {
        val oldZoom = currentZoom
        val oldCenterX = centerX
        val oldCenterY = centerY

        // Calculate new zoom level
        val newZoom = MathUtils.clamp(
            currentZoom * scaleFactor,
            minZoom,
            maxZoom
        )

        // Calculate zoom delta
        val zoomDelta = newZoom / currentZoom

        // Adjust center point to zoom around focus
        val newCenterX = focusX - (focusX - centerX) * zoomDelta
        val newCenterY = focusY - (focusY - centerY) * zoomDelta

        // Apply constraints to center
        val constrainedCenter = constrainCenter(newCenterX, newCenterY, newZoom)

        // Update state
        currentZoom = newZoom
        centerX = constrainedCenter.x
        centerY = constrainedCenter.y

        return ZoomResult(
            newZoom = currentZoom,
            newCenterX = centerX,
            newCenterY = centerY,
            zoomChanged = oldZoom != currentZoom,
            centerChanged = oldCenterX != centerX || oldCenterY != centerY
        )
    }

    /**
     * Sets zoom level with optional center point
     */
    fun setZoom(zoom: Float, centerX: Float = this.centerX, centerY: Float = this.centerY): ZoomResult {
        val oldZoom = currentZoom
        val oldCenterX = this.centerX
        val oldCenterY = this.centerY

        // Clamp zoom to constraints
        val newZoom = MathUtils.clamp(zoom, minZoom, maxZoom)

        // Constrain center point
        val constrainedCenter = constrainCenter(centerX, centerY, newZoom)

        // Update state
        currentZoom = newZoom
        this.centerX = constrainedCenter.x
        this.centerY = constrainedCenter.y

        return ZoomResult(
            newZoom = currentZoom,
            newCenterX = this.centerX,
            newCenterY = this.centerY,
            zoomChanged = oldZoom != currentZoom,
            centerChanged = oldCenterX != this.centerX || oldCenterY != this.centerY
        )
    }

    /**
     * Zooms to fit content in viewport
     */
    fun zoomToFit(): ZoomResult {
        val fitZoom = calculateFitZoom()
        val centerPoint = calculateFitCenter(fitZoom)

        return setZoom(fitZoom, centerPoint.x, centerPoint.y)
    }

    /**
     * Zooms to fit width
     */
    fun zoomToFitWidth(): ZoomResult {
        val widthZoom = if (contentWidth > 0) viewportWidth / contentWidth else 1f
        val clampedZoom = MathUtils.clamp(widthZoom, minZoom, maxZoom)
        val centerPoint = calculateFitCenter(clampedZoom)

        return setZoom(clampedZoom, centerPoint.x, centerPoint.y)
    }

    /**
     * Zooms to fit height
     */
    fun zoomToFitHeight(): ZoomResult {
        val heightZoom = if (contentHeight > 0) viewportHeight / contentHeight else 1f
        val clampedZoom = MathUtils.clamp(heightZoom, minZoom, maxZoom)
        val centerPoint = calculateFitCenter(clampedZoom)

        return setZoom(clampedZoom, centerPoint.x, centerPoint.y)
    }

    /**
     * Handles double tap zoom
     */
    fun handleDoubleTapZoom(tapX: Float, tapY: Float): ZoomResult {
        val doubleTapZoom = configuration.doubleTapZoomScale

        val targetZoom = if (abs(currentZoom - doubleTapZoom) < 0.1f) {
            // Already at double tap zoom, zoom out to fit
            calculateFitZoom()
        } else {
            // Zoom in to double tap level
            MathUtils.clamp(doubleTapZoom, minZoom, maxZoom)
        }

        // Determine center based on zoom direction
        val targetCenter = if (targetZoom > currentZoom) {
            // Zooming in - center on tap point
            PointF(tapX, tapY)
        } else {
            // Zooming out - center content
            calculateFitCenter(targetZoom)
        }

        return if (configuration.zoomCentered) {
            // Always center zoom
            val fitCenter = calculateFitCenter(targetZoom)
            setZoom(targetZoom, fitCenter.x, fitCenter.y)
        } else {
            // Zoom around tap point
            setZoom(targetZoom, targetCenter.x, targetCenter.y)
        }
    }

    /**
     * Starts animated zoom to target
     */
    fun animateToZoom(
        targetZoom: Float,
        targetCenterX: Float = centerX,
        targetCenterY: Float = centerY,
        duration: Long = 300L
    ) {
        isAnimating = true
        animationStartTime = System.currentTimeMillis()
        animationDuration = duration
        animationStartZoom = currentZoom
        animationTargetZoom = MathUtils.clamp(targetZoom, minZoom, maxZoom)
        animationStartCenter.set(centerX, centerY)

        val constrainedTarget = constrainCenter(targetCenterX, targetCenterY, animationTargetZoom)
        animationTargetCenter.set(constrainedTarget.x, constrainedTarget.y)
    }

    /**
     * Updates animated zoom (should be called from animation loop)
     */
    fun updateAnimation(): ZoomResult? {
        if (!isAnimating) return null

        val elapsed = System.currentTimeMillis() - animationStartTime
        val progress = MathUtils.clamp(elapsed.toFloat() / animationDuration, 0f, 1f)

        if (progress >= 1f) {
            // Animation complete
            isAnimating = false
            return setZoom(animationTargetZoom, animationTargetCenter.x, animationTargetCenter.y)
        }

        // Interpolate zoom and center
        val easedProgress = easeInOutCubic(progress)
        val interpolatedZoom = MathUtils.lerp(animationStartZoom, animationTargetZoom, easedProgress)
        val interpolatedCenterX = MathUtils.lerp(animationStartCenter.x, animationTargetCenter.x, easedProgress)
        val interpolatedCenterY = MathUtils.lerp(animationStartCenter.y, animationTargetCenter.y, easedProgress)

        return setZoom(interpolatedZoom, interpolatedCenterX, interpolatedCenterY)
    }

    /**
     * Stops current zoom animation
     */
    fun stopAnimation() {
        isAnimating = false
    }

    private fun updateZoomConstraints() {
        minZoom = configuration.minZoom
        maxZoom = configuration.maxZoom

        // Ensure current zoom is within new constraints
        if (currentZoom < minZoom || currentZoom > maxZoom) {
            currentZoom = MathUtils.clamp(currentZoom, minZoom, maxZoom)
        }
    }

    private fun calculateFitZoom(): Float {
        if (contentWidth <= 0 || contentHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            return 1f
        }

        val scaleX = viewportWidth / contentWidth
        val scaleY = viewportHeight / contentHeight
        val fitScale = min(scaleX, scaleY)

        return MathUtils.clamp(fitScale, minZoom, maxZoom)
    }

    private fun calculateFitCenter(zoom: Float): PointF {
        val scaledContentWidth = contentWidth * zoom
        val scaledContentHeight = contentHeight * zoom

        val centerX = if (scaledContentWidth > viewportWidth) {
            viewportWidth / 2f
        } else {
            scaledContentWidth / 2f
        }

        val centerY = if (scaledContentHeight > viewportHeight) {
            viewportHeight / 2f
        } else {
            scaledContentHeight / 2f
        }

        return PointF(centerX, centerY)
    }

    private fun constrainCenter(centerX: Float, centerY: Float, zoom: Float): PointF {
        val scaledContentWidth = contentWidth * zoom
        val scaledContentHeight = contentHeight * zoom

        // Calculate bounds for center point
        val minCenterX = min(viewportWidth / 2f, scaledContentWidth / 2f)
        val maxCenterX = max(viewportWidth / 2f, scaledContentWidth - viewportWidth / 2f)
        val minCenterY = min(viewportHeight / 2f, scaledContentHeight / 2f)
        val maxCenterY = max(viewportHeight / 2f, scaledContentHeight - viewportHeight / 2f)

        val constrainedX = MathUtils.clamp(centerX, minCenterX, maxCenterX)
        val constrainedY = MathUtils.clamp(centerY, minCenterY, maxCenterY)

        return PointF(constrainedX, constrainedY)
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4f * t * t * t
        } else {
            val shifted = t - 1f
            1f + 4f * shifted * shifted * shifted
        }
    }

    /**
     * Gets current zoom level
     */
    fun getCurrentZoom(): Float = currentZoom

    /**
     * Gets current center point
     */
    fun getCurrentCenter(): PointF = PointF(centerX, centerY)

    /**
     * Gets zoom constraints
     */
    fun getZoomConstraints(): Pair<Float, Float> = Pair(minZoom, maxZoom)

    /**
     * Checks if can zoom in further
     */
    fun canZoomIn(): Boolean = currentZoom < maxZoom

    /**
     * Checks if can zoom out further
     */
    fun canZoomOut(): Boolean = currentZoom > minZoom

    /**
     * Checks if zoom animation is active
     */
    fun isAnimating(): Boolean = isAnimating

    /**
     * Gets zoom level as percentage
     */
    fun getZoomPercentage(): Int = (currentZoom * 100).toInt()

    /**
     * Updates configuration
     */
    fun updateConfiguration(newConfig: Configuration) {
        configuration = newConfig
        updateZoomConstraints()
    }

    /**
     * Resets zoom to default
     */
    fun reset(): ZoomResult {
        return setZoom(1f, viewportWidth / 2f, viewportHeight / 2f)
    }

    /**
     * Gets zoom handler statistics
     */
    fun getStatistics(): ZoomStatistics {
        return ZoomStatistics(
            currentZoom = currentZoom,
            currentCenter = PointF(centerX, centerY),
            minZoom = minZoom,
            maxZoom = maxZoom,
            isAnimating = isAnimating,
            canZoomIn = canZoomIn(),
            canZoomOut = canZoomOut(),
            zoomPercentage = getZoomPercentage(),
            viewportSize = PointF(viewportWidth, viewportHeight),
            contentSize = PointF(contentWidth, contentHeight)
        )
    }

    data class ZoomStatistics(
        val currentZoom: Float,
        val currentCenter: PointF,
        val minZoom: Float,
        val maxZoom: Float,
        val isAnimating: Boolean,
        val canZoomIn: Boolean,
        val canZoomOut: Boolean,
        val zoomPercentage: Int,
        val viewportSize: PointF,
        val contentSize: PointF
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Zoom Handler Statistics:")
                appendLine("  Current Zoom: ${String.format("%.2f", currentZoom)} ($zoomPercentage%)")
                appendLine("  Center: (${currentCenter.x.toInt()}, ${currentCenter.y.toInt()})")
                appendLine("  Constraints: ${String.format("%.2f", minZoom)} - ${String.format("%.2f", maxZoom)}")
                appendLine("  Can Zoom: in=$canZoomIn, out=$canZoomOut")
                appendLine("  Animating: $isAnimating")
                appendLine("  Viewport: ${viewportSize.x.toInt()}x${viewportSize.y.toInt()}")
                appendLine("  Content: ${contentSize.x.toInt()}x${contentSize.y.toInt()}")
            }
        }
    }
}