package com.sonpxp.pdfloader.layout


import android.graphics.PointF
import android.graphics.RectF
import com.sonpxp.pdfloader.FitPolicy
import com.sonpxp.pdfloader.model.Configuration
import com.sonpxp.pdfloader.utils.MathUtils
import kotlin.math.*

/**
 * Manages viewport calculations and transformations
 * Handles coordinate conversions, visibility calculations, and viewport bounds
 */
class ViewportCalculator(
    private val configuration: Configuration
) {

    private var viewportBounds = RectF()
    private var contentBounds = RectF()
    private var currentZoom = 1f
    private var currentScroll = PointF(0f, 0f)
    private var viewportCenter = PointF(0f, 0f)

    /**
     * Viewport information
     */
    data class ViewportInfo(
        val bounds: RectF,
        val center: PointF,
        val zoom: Float,
        val scroll: PointF,
        val visibleArea: RectF,
        val contentBounds: RectF,
        val canScrollHorizontal: Boolean,
        val canScrollVertical: Boolean
    ) {
        /**
         * Gets the viewport aspect ratio
         */
        fun getAspectRatio(): Float {
            return if (bounds.height() > 0) bounds.width() / bounds.height() else 1f
        }

        /**
         * Checks if a point is visible in the viewport
         */
        fun isPointVisible(x: Float, y: Float): Boolean {
            return visibleArea.contains(x, y)
        }

        /**
         * Checks if a rectangle intersects with the viewport
         */
        fun intersects(rect: RectF): Boolean {
            return RectF.intersects(visibleArea, rect)
        }
    }

    /**
     * Viewport transformation result
     */
    data class ViewportTransform(
        val scale: Float,
        val translation: PointF,
        val rotation: Float = 0f,
        val pivot: PointF = PointF(0f, 0f)
    )

    /**
     * Updates viewport dimensions
     */
    fun setViewportBounds(bounds: RectF) {
        viewportBounds.set(bounds)
        viewportCenter.set(bounds.centerX(), bounds.centerY())
    }

    /**
     * Updates content bounds
     */
    fun setContentBounds(bounds: RectF) {
        contentBounds.set(bounds)
    }

    /**
     * Updates current zoom level
     */
    fun setZoom(zoom: Float) {
        currentZoom = MathUtils.clamp(zoom, configuration.minZoom, configuration.maxZoom)
    }

    /**
     * Updates scroll position
     */
    fun setScroll(scroll: PointF) {
        currentScroll.set(
            clampScrollX(scroll.x),
            clampScrollY(scroll.y)
        )
    }

    /**
     * Gets current viewport information
     */
    fun getViewportInfo(): ViewportInfo {
        val visibleArea = calculateVisibleArea()

        return ViewportInfo(
            bounds = RectF(viewportBounds),
            center = PointF(viewportCenter),
            zoom = currentZoom,
            scroll = PointF(currentScroll),
            visibleArea = visibleArea,
            contentBounds = RectF(contentBounds),
            canScrollHorizontal = canScrollHorizontally(),
            canScrollVertical = canScrollVertically()
        )
    }

    /**
     * Calculates the visible area in content coordinates
     */
    fun calculateVisibleArea(): RectF {
        val scaledViewportWidth = viewportBounds.width() / currentZoom
        val scaledViewportHeight = viewportBounds.height() / currentZoom

        return RectF(
            currentScroll.x,
            currentScroll.y,
            currentScroll.x + scaledViewportWidth,
            currentScroll.y + scaledViewportHeight
        )
    }

    /**
     * Converts screen coordinates to content coordinates
     */
    fun screenToContent(screenX: Float, screenY: Float): PointF {
        val contentX = (screenX / currentZoom) + currentScroll.x
        val contentY = (screenY / currentZoom) + currentScroll.y

        return PointF(contentX, contentY)
    }

    /**
     * Converts content coordinates to screen coordinates
     */
    fun contentToScreen(contentX: Float, contentY: Float): PointF {
        val screenX = (contentX - currentScroll.x) * currentZoom
        val screenY = (contentY - currentScroll.y) * currentZoom

        return PointF(screenX, screenY)
    }

    /**
     * Calculates zoom level to fit content in viewport
     */
    fun calculateZoomToFit(contentWidth: Float, contentHeight: Float, fitPolicy: FitPolicy = configuration.pageFitPolicy): Float {
        if (viewportBounds.isEmpty || contentWidth <= 0 || contentHeight <= 0) {
            return 1f
        }

        val viewportWidth = viewportBounds.width()
        val viewportHeight = viewportBounds.height()

        val scale = when (fitPolicy) {
            FitPolicy.WIDTH -> viewportWidth / contentWidth
            FitPolicy.HEIGHT -> viewportHeight / contentHeight
            FitPolicy.BOTH -> min(viewportWidth / contentWidth, viewportHeight / contentHeight)
            FitPolicy.AUTO -> {
                val widthScale = viewportWidth / contentWidth
                val heightScale = viewportHeight / contentHeight

                // Choose scale that shows more content while fitting in viewport
                if (contentWidth > contentHeight) {
                    min(widthScale, heightScale) // Landscape content
                } else {
                    min(widthScale, heightScale) // Portrait content
                }
            }
        }

        return MathUtils.clamp(scale, configuration.minZoom, configuration.maxZoom)
    }

    /**
     * Calculates zoom level to fit viewport width
     */
    fun calculateZoomToFitWidth(contentWidth: Float): Float {
        if (contentWidth <= 0 || viewportBounds.isEmpty) return 1f

        val scale = viewportBounds.width() / contentWidth
        return MathUtils.clamp(scale, configuration.minZoom, configuration.maxZoom)
    }

    /**
     * Calculates zoom level to fit viewport height
     */
    fun calculateZoomToFitHeight(contentHeight: Float): Float {
        if (contentHeight <= 0 || viewportBounds.isEmpty) return 1f

        val scale = viewportBounds.height() / contentHeight
        return MathUtils.clamp(scale, configuration.minZoom, configuration.maxZoom)
    }

    /**
     * Calculates scroll position to center content in viewport
     */
    fun calculateScrollToCenter(contentBounds: RectF): PointF {
        val scaledContentWidth = contentBounds.width() * currentZoom
        val scaledContentHeight = contentBounds.height() * currentZoom

        val centerX = (scaledContentWidth - viewportBounds.width()) / 2f / currentZoom
        val centerY = (scaledContentHeight - viewportBounds.height()) / 2f / currentZoom

        return PointF(
            clampScrollX(centerX),
            clampScrollY(centerY)
        )
    }

    /**
     * Calculates scroll position to show specific point at viewport center
     */
    fun calculateScrollToCenterPoint(contentX: Float, contentY: Float): PointF {
        val scrollX = contentX - (viewportBounds.width() / 2f) / currentZoom
        val scrollY = contentY - (viewportBounds.height() / 2f) / currentZoom

        return PointF(
            clampScrollX(scrollX),
            clampScrollY(scrollY)
        )
    }

    /**
     * Calculates scroll bounds
     */
    fun calculateScrollBounds(): RectF {
        val maxScrollX = max(0f, contentBounds.width() * currentZoom - viewportBounds.width()) / currentZoom
        val maxScrollY = max(0f, contentBounds.height() * currentZoom - viewportBounds.height()) / currentZoom

        return RectF(0f, 0f, maxScrollX, maxScrollY)
    }

    /**
     * Checks if content can scroll horizontally
     */
    fun canScrollHorizontally(): Boolean {
        val scaledContentWidth = contentBounds.width() * currentZoom
        return scaledContentWidth > viewportBounds.width()
    }

    /**
     * Checks if content can scroll vertically
     */
    fun canScrollVertically(): Boolean {
        val scaledContentHeight = contentBounds.height() * currentZoom
        return scaledContentHeight > viewportBounds.height()
    }

    /**
     * Clamps horizontal scroll position
     */
    private fun clampScrollX(scrollX: Float): Float {
        val scrollBounds = calculateScrollBounds()
        return MathUtils.clamp(scrollX, scrollBounds.left, scrollBounds.right)
    }

    /**
     * Clamps vertical scroll position
     */
    private fun clampScrollY(scrollY: Float): Float {
        val scrollBounds = calculateScrollBounds()
        return MathUtils.clamp(scrollY, scrollBounds.top, scrollBounds.bottom)
    }

    /**
     * Calculates visibility percentage of a rectangle
     */
    fun calculateVisibilityPercentage(rect: RectF): Float {
        val visibleArea = calculateVisibleArea()
        val intersection = RectF()

        if (!intersection.setIntersect(rect, visibleArea)) {
            return 0f
        }

        val intersectionArea = intersection.width() * intersection.height()
        val rectArea = rect.width() * rect.height()

        return if (rectArea > 0) intersectionArea / rectArea else 0f
    }

    /**
     * Finds the best zoom level for reading
     */
    fun calculateReadingZoom(textSize: Float = 12f, targetTextSize: Float = 16f): Float {
        val zoomForReading = targetTextSize / textSize
        return MathUtils.clamp(zoomForReading, configuration.minZoom, configuration.maxZoom)
    }

    /**
     * Calculates zoom and scroll to focus on a specific area
     */
    fun calculateFocusTransform(targetRect: RectF, padding: Float = 20f): ViewportTransform {
        // Add padding to target rectangle
        val paddedRect = RectF(
            targetRect.left - padding,
            targetRect.top - padding,
            targetRect.right + padding,
            targetRect.bottom + padding
        )

        // Calculate zoom to fit the padded rectangle
        val zoomToFit = calculateZoomToFit(paddedRect.width(), paddedRect.height(), FitPolicy.BOTH)

        // Calculate scroll to center the rectangle
        val centerScroll = calculateScrollToCenterPoint(paddedRect.centerX(), paddedRect.centerY())

        return ViewportTransform(
            scale = zoomToFit,
            translation = centerScroll,
            pivot = PointF(viewportCenter.x, viewportCenter.y)
        )
    }

    /**
     * Calculates transform for double-tap zoom
     */
    fun calculateDoubleTapTransform(tapX: Float, tapY: Float): ViewportTransform {
        val doubleTapZoom = configuration.doubleTapZoomScale

        val newZoom = if (abs(currentZoom - doubleTapZoom) < 0.1f) {
            // If already at double-tap zoom, zoom out to fit
            calculateZoomToFit(contentBounds.width(), contentBounds.height())
        } else {
            // Zoom in to double-tap level
            MathUtils.clamp(doubleTapZoom, configuration.minZoom, configuration.maxZoom)
        }

        // Calculate scroll to keep tap point centered if zooming in
        val tapPoint = screenToContent(tapX, tapY)
        val newScroll = if (newZoom > currentZoom) {
            calculateScrollToCenterPoint(tapPoint.x, tapPoint.y)
        } else {
            calculateScrollToCenter(contentBounds)
        }

        return ViewportTransform(
            scale = newZoom,
            translation = newScroll,
            pivot = PointF(tapX, tapY)
        )
    }

    /**
     * Optimizes viewport for performance
     */
    fun optimizeViewport(): ViewportOptimization {
        val info = getViewportInfo()
        val suggestions = mutableListOf<String>()

        // Check zoom level
        when {
            currentZoom < 0.25f -> suggestions.add("Zoom level very low - consider minimum zoom limit")
            currentZoom > 8f -> suggestions.add("Zoom level very high - may impact performance")
        }

        // Check scroll bounds
        if (!canScrollHorizontally() && !canScrollVertically()) {
            suggestions.add("Content fits entirely in viewport - consider optimizing layout")
        }

        // Check aspect ratio mismatch
        val contentAspectRatio = if (contentBounds.height() > 0) contentBounds.width() / contentBounds.height() else 1f
        val viewportAspectRatio = info.getAspectRatio()
        val aspectRatioDiff = abs(contentAspectRatio - viewportAspectRatio)

        if (aspectRatioDiff > 1f) {
            suggestions.add("Large aspect ratio mismatch - consider adjusting fit policy")
        }

        return ViewportOptimization(
            isOptimal = suggestions.isEmpty(),
            suggestions = suggestions,
            currentZoom = currentZoom,
            recommendedZoom = calculateZoomToFit(contentBounds.width(), contentBounds.height()),
            memoryUsage = estimateMemoryUsage(),
            performanceScore = calculatePerformanceScore()
        )
    }

    /**
     * Estimates memory usage based on viewport
     */
    private fun estimateMemoryUsage(): Long {
        val visiblePixels = viewportBounds.width() * viewportBounds.height() * currentZoom * currentZoom
        return (visiblePixels * 4).toLong() // 4 bytes per pixel for ARGB
    }

    /**
     * Calculates performance score (0-100)
     */
    private fun calculatePerformanceScore(): Int {
        var score = 100

        // Deduct points for high zoom levels
        when {
            currentZoom > 4f -> score -= 30
            currentZoom > 2f -> score -= 15
            currentZoom < 0.5f -> score -= 10
        }

        // Deduct points for large viewport
        val viewportSize = viewportBounds.width() * viewportBounds.height()
        when {
            viewportSize > 2_000_000 -> score -= 20 // Very large viewport
            viewportSize > 1_000_000 -> score -= 10 // Large viewport
        }

        // Deduct points for excessive scrollable area
        val scrollableArea = contentBounds.width() * contentBounds.height() * currentZoom * currentZoom
        when {
            scrollableArea > 10_000_000 -> score -= 25
            scrollableArea > 5_000_000 -> score -= 15
        }

        return maxOf(0, score)
    }

    /**
     * Gets viewport boundaries for a specific zoom level
     */
    fun getViewportBoundsAtZoom(zoom: Float): RectF {
        val scaledWidth = viewportBounds.width() / zoom
        val scaledHeight = viewportBounds.height() / zoom

        return RectF(
            currentScroll.x,
            currentScroll.y,
            currentScroll.x + scaledWidth,
            currentScroll.y + scaledHeight
        )
    }

    /**
     * Calculates the minimum zoom that shows all content
     */
    fun calculateMinZoomToShowAll(): Float {
        if (contentBounds.isEmpty || viewportBounds.isEmpty) return configuration.minZoom

        val scaleX = viewportBounds.width() / contentBounds.width()
        val scaleY = viewportBounds.height() / contentBounds.height()
        val minScale = min(scaleX, scaleY)

        return maxOf(minScale, configuration.minZoom)
    }

    /**
     * Calculates the maximum useful zoom level
     */
    fun calculateMaxUsefulZoom(maxTextureSize: Int = 4096): Float {
        val maxScaleX = maxTextureSize / viewportBounds.width()
        val maxScaleY = maxTextureSize / viewportBounds.height()
        val maxScale = min(maxScaleX, maxScaleY)

        return minOf(maxScale, configuration.maxZoom)
    }

    /**
     * Creates a viewport transform for smooth transitions
     */
    fun createSmoothTransform(
        targetZoom: Float,
        targetScroll: PointF,
        duration: Long = 300
    ): SmoothTransform {
        val startZoom = currentZoom
        val startScroll = PointF(currentScroll)

        return SmoothTransform(
            startZoom = startZoom,
            targetZoom = MathUtils.clamp(targetZoom, configuration.minZoom, configuration.maxZoom),
            startScroll = startScroll,
            targetScroll = PointF(clampScrollX(targetScroll.x), clampScrollY(targetScroll.y)),
            duration = duration
        )
    }

    /**
     * Calculates viewport state for gesture handling
     */
    fun calculateGestureViewport(
        gestureCenter: PointF,
        scaleFactor: Float,
        translation: PointF
    ): ViewportTransform {
        // Calculate new zoom level
        val newZoom = MathUtils.clamp(
            currentZoom * scaleFactor,
            configuration.minZoom,
            configuration.maxZoom
        )

        // Calculate new scroll position accounting for gesture center
        val zoomDelta = newZoom / currentZoom
        val gestureCenterContent = screenToContent(gestureCenter.x, gestureCenter.y)

        val newScrollX = gestureCenterContent.x - (gestureCenter.x / newZoom) + (translation.x / newZoom)
        val newScrollY = gestureCenterContent.y - (gestureCenter.y / newZoom) + (translation.y / newZoom)

        return ViewportTransform(
            scale = newZoom,
            translation = PointF(clampScrollX(newScrollX), clampScrollY(newScrollY)),
            pivot = gestureCenter
        )
    }

    /**
     * Handles viewport changes during rotation
     */
    fun handleRotationChange(newViewportBounds: RectF): ViewportTransform {
        val oldCenter = PointF(viewportCenter)
        setViewportBounds(newViewportBounds)

        // Try to maintain the same content center
        val centerContentPoint = screenToContent(oldCenter.x, oldCenter.y)
        val newScroll = calculateScrollToCenterPoint(centerContentPoint.x, centerContentPoint.y)

        // Recalculate zoom if needed to fit content
        val newZoom = if (configuration.fitEachPage) {
            calculateZoomToFit(contentBounds.width(), contentBounds.height())
        } else {
            currentZoom
        }

        return ViewportTransform(
            scale = newZoom,
            translation = newScroll
        )
    }

    /**
     * Data classes for viewport calculations
     */
    data class ViewportOptimization(
        val isOptimal: Boolean,
        val suggestions: List<String>,
        val currentZoom: Float,
        val recommendedZoom: Float,
        val memoryUsage: Long,
        val performanceScore: Int
    ) {
        fun getMemoryUsageMB(): Float = memoryUsage / (1024f * 1024f)

        override fun toString(): String {
            return buildString {
                appendLine("Viewport Optimization:")
                appendLine("  Optimal: $isOptimal")
                appendLine("  Current Zoom: ${String.format("%.2f", currentZoom)}")
                appendLine("  Recommended Zoom: ${String.format("%.2f", recommendedZoom)}")
                appendLine("  Memory Usage: ${String.format("%.2f", getMemoryUsageMB())} MB")
                appendLine("  Performance Score: $performanceScore/100")
                if (suggestions.isNotEmpty()) {
                    appendLine("  Suggestions:")
                    suggestions.forEach { appendLine("    - $it") }
                }
            }
        }
    }

    data class SmoothTransform(
        val startZoom: Float,
        val targetZoom: Float,
        val startScroll: PointF,
        val targetScroll: PointF,
        val duration: Long
    ) {
        /**
         * Calculates intermediate state for animation
         */
        fun interpolate(progress: Float): ViewportTransform {
            val clampedProgress = MathUtils.clamp(progress, 0f, 1f)
            val easedProgress = easeInOutCubic(clampedProgress)

            val interpolatedZoom = MathUtils.lerp(startZoom, targetZoom, easedProgress)
            val interpolatedScrollX = MathUtils.lerp(startScroll.x, targetScroll.x, easedProgress)
            val interpolatedScrollY = MathUtils.lerp(startScroll.y, targetScroll.y, easedProgress)

            return ViewportTransform(
                scale = interpolatedZoom,
                translation = PointF(interpolatedScrollX, interpolatedScrollY)
            )
        }

        private fun easeInOutCubic(t: Float): Float {
            return if (t < 0.5f) {
                4f * t * t * t
            } else {
                val shifted = t - 1f
                1f + 4f * shifted * shifted * shifted
            }
        }
    }

    /**
     * Gets comprehensive viewport statistics
     */
    fun getViewportStatistics(): ViewportStatistics {
        val info = getViewportInfo()
        val scrollBounds = calculateScrollBounds()
        val optimization = optimizeViewport()

        return ViewportStatistics(
            viewportSize = PointF(viewportBounds.width(), viewportBounds.height()),
            contentSize = PointF(contentBounds.width(), contentBounds.height()),
            currentZoom = currentZoom,
            currentScroll = PointF(currentScroll),
            visibleArea = calculateVisibleArea(),
            scrollBounds = scrollBounds,
            canScrollHorizontal = info.canScrollHorizontal,
            canScrollVertical = info.canScrollVertical,
            memoryUsageMB = optimization.getMemoryUsageMB(),
            performanceScore = optimization.performanceScore,
            aspectRatio = info.getAspectRatio()
        )
    }

    data class ViewportStatistics(
        val viewportSize: PointF,
        val contentSize: PointF,
        val currentZoom: Float,
        val currentScroll: PointF,
        val visibleArea: RectF,
        val scrollBounds: RectF,
        val canScrollHorizontal: Boolean,
        val canScrollVertical: Boolean,
        val memoryUsageMB: Float,
        val performanceScore: Int,
        val aspectRatio: Float
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Viewport Statistics:")
                appendLine("  Viewport: ${viewportSize.x.toInt()}x${viewportSize.y.toInt()}")
                appendLine("  Content: ${contentSize.x.toInt()}x${contentSize.y.toInt()}")
                appendLine("  Zoom: ${String.format("%.2f", currentZoom)} (${(currentZoom * 100).toInt()}%)")
                appendLine("  Scroll: (${currentScroll.x.toInt()}, ${currentScroll.y.toInt()})")
                appendLine("  Visible Area: ${visibleArea.width().toInt()}x${visibleArea.height().toInt()}")
                appendLine("  Scroll Bounds: ${scrollBounds.width().toInt()}x${scrollBounds.height().toInt()}")
                appendLine("  Can Scroll: H=$canScrollHorizontal, V=$canScrollVertical")
                appendLine("  Memory Usage: ${String.format("%.2f", memoryUsageMB)} MB")
                appendLine("  Performance: $performanceScore/100")
                appendLine("  Aspect Ratio: ${String.format("%.2f", aspectRatio)}")
            }
        }
    }
}