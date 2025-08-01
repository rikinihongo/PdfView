package com.sonpxp.pdfloader.gestures


import android.graphics.PointF
import com.sonpxp.pdfloader.ScrollDirection
import com.sonpxp.pdfloader.model.Configuration
import com.sonpxp.pdfloader.utils.MathUtils

/**
 * Handles scroll operations and transformations
 * Manages scroll position, bounds checking, and scroll constraints
 */
class ScrollHandler(
    private var configuration: Configuration
) {

    // Current scroll state
    private var scrollX = 0f
    private var scrollY = 0f

    // Content and viewport dimensions
    private var contentWidth = 0f
    private var contentHeight = 0f
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var currentZoom = 1f

    // Scroll bounds
    private var minScrollX = 0f
    private var maxScrollX = 0f
    private var minScrollY = 0f
    private var maxScrollY = 0f

    // Over-scroll settings
    private var overScrollEnabled = true
    private var overScrollDistance = 100f // pixels
    private var currentOverScrollX = 0f
    private var currentOverScrollY = 0f

    data class ScrollResult(
        val newScrollX: Float,
        val newScrollY: Float,
        val scrollChanged: Boolean,
        val hitBoundary: Boolean,
        val overScrollX: Float,
        val overScrollY: Float
    )

    /**
     * Sets dimensions and zoom level
     */
    fun setDimensions(
        contentWidth: Float,
        contentHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        zoom: Float
    ) {
        this.contentWidth = contentWidth
        this.contentHeight = contentHeight
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        this.currentZoom = zoom

        updateScrollBounds()
        constrainScrollPosition()
    }

    /**
     * Handles scroll gesture
     */
    fun handleScroll(deltaX: Float, deltaY: Float): ScrollResult {
        val oldScrollX = scrollX
        val oldScrollY = scrollY

        // Apply scroll direction constraints
        val constrainedDelta = applyScrollDirectionConstraints(deltaX, deltaY)

        // Calculate new scroll position
        var newScrollX = scrollX - constrainedDelta.first // Invert delta for natural scrolling
        var newScrollY = scrollY - constrainedDelta.second

        // Handle over-scroll
        val scrollResult = applyScrollBounds(newScrollX, newScrollY)

        // Update scroll position
        scrollX = scrollResult.newScrollX
        scrollY = scrollResult.newScrollY
        currentOverScrollX = scrollResult.overScrollX
        currentOverScrollY = scrollResult.overScrollY

        return ScrollResult(
            newScrollX = scrollX,
            newScrollY = scrollY,
            scrollChanged = oldScrollX != scrollX || oldScrollY != scrollY,
            hitBoundary = scrollResult.hitBoundary,
            overScrollX = currentOverScrollX,
            overScrollY = currentOverScrollY
        )
    }

    /**
     * Sets scroll position directly
     */
    fun setScroll(x: Float, y: Float): ScrollResult {
        val oldScrollX = scrollX
        val oldScrollY = scrollY

        // Apply constraints
        val constrainedScroll = constrainScrollToContent(x, y)
        scrollX = constrainedScroll.x
        scrollY = constrainedScroll.y

        // Reset over-scroll when setting position directly
        currentOverScrollX = 0f
        currentOverScrollY = 0f

        return ScrollResult(
            newScrollX = scrollX,
            newScrollY = scrollY,
            scrollChanged = oldScrollX != scrollX || oldScrollY != scrollY,
            hitBoundary = scrollX == minScrollX || scrollX == maxScrollX ||
                    scrollY == minScrollY || scrollY == maxScrollY,
            overScrollX = 0f,
            overScrollY = 0f
        )
    }

    /**
     * Scrolls to center content
     */
    fun scrollToCenter(): ScrollResult {
        val centerX = (maxScrollX + minScrollX) / 2f
        val centerY = (maxScrollY + minScrollY) / 2f
        return setScroll(centerX, centerY)
    }

    /**
     * Scrolls to specific point in content
     */
    fun scrollToPoint(contentX: Float, contentY: Float): ScrollResult {
        // Convert content coordinates to scroll coordinates
        val scrollX = contentX - viewportWidth / 2f / currentZoom
        val scrollY = contentY - viewportHeight / 2f / currentZoom

        return setScroll(scrollX, scrollY)
    }

    /**
     * Scrolls by specific amount
     */
    fun scrollBy(deltaX: Float, deltaY: Float): ScrollResult {
        return handleScroll(deltaX, deltaY)
    }

    /**
     * Animates scroll to target position
     */
    fun animateScrollTo(
        targetX: Float,
        targetY: Float,
        duration: Long = 300L
    ): ScrollAnimation {
        val constrainedTarget = constrainScrollToContent(targetX, targetY)

        return ScrollAnimation(
            startX = scrollX,
            startY = scrollY,
            targetX = constrainedTarget.x,
            targetY = constrainedTarget.y,
            duration = duration,
            startTime = System.currentTimeMillis()
        )
    }

    /**
     * Updates scroll animation
     */
    fun updateScrollAnimation(animation: ScrollAnimation): ScrollResult? {
        val elapsed = System.currentTimeMillis() - animation.startTime
        val progress = MathUtils.clamp(elapsed.toFloat() / animation.duration, 0f, 1f)

        if (progress >= 1f) {
            // Animation complete
            return setScroll(animation.targetX, animation.targetY)
        }

        // Interpolate position
        val easedProgress = easeOutCubic(progress)
        val interpolatedX = MathUtils.lerp(animation.startX, animation.targetX, easedProgress)
        val interpolatedY = MathUtils.lerp(animation.startY, animation.targetY, easedProgress)

        return setScroll(interpolatedX, interpolatedY)
    }

    private fun applyScrollDirectionConstraints(deltaX: Float, deltaY: Float): Pair<Float, Float> {
        return when (configuration.pageScrollDirection) {
            ScrollDirection.HORIZONTAL -> Pair(deltaX, 0f)
            ScrollDirection.VERTICAL -> Pair(0f, deltaY)
            ScrollDirection.BOTH -> Pair(deltaX, deltaY)
        }
    }

    private fun applyScrollBounds(x: Float, y: Float): ScrollBoundsResult {
        var newX = x
        var newY = y
        var overScrollX = 0f
        var overScrollY = 0f
        var hitBoundary = false

        // Handle X bounds
        when {
            newX < minScrollX -> {
                if (overScrollEnabled) {
                    overScrollX = newX - minScrollX
                    if (kotlin.math.abs(overScrollX) > overScrollDistance) {
                        overScrollX = -overScrollDistance
                        newX = minScrollX - overScrollDistance
                    }
                } else {
                    newX = minScrollX
                    hitBoundary = true
                }
            }
            newX > maxScrollX -> {
                if (overScrollEnabled) {
                    overScrollX = newX - maxScrollX
                    if (overScrollX > overScrollDistance) {
                        overScrollX = overScrollDistance
                        newX = maxScrollX + overScrollDistance
                    }
                } else {
                    newX = maxScrollX
                    hitBoundary = true
                }
            }
            else -> {
                newX = MathUtils.clamp(x, minScrollX, maxScrollX)
            }
        }

        // Handle Y bounds
        when {
            newY < minScrollY -> {
                if (overScrollEnabled) {
                    overScrollY = newY - minScrollY
                    if (kotlin.math.abs(overScrollY) > overScrollDistance) {
                        overScrollY = -overScrollDistance
                        newY = minScrollY - overScrollDistance
                    }
                } else {
                    newY = minScrollY
                    hitBoundary = true
                }
            }
            newY > maxScrollY -> {
                if (overScrollEnabled) {
                    overScrollY = newY - maxScrollY
                    if (overScrollY > overScrollDistance) {
                        overScrollY = overScrollDistance
                        newY = maxScrollY + overScrollDistance
                    }
                } else {
                    newY = maxScrollY
                    hitBoundary = true
                }
            }
            else -> {
                newY = MathUtils.clamp(y, minScrollY, maxScrollY)
            }
        }

        return ScrollBoundsResult(newX, newY, overScrollX, overScrollY, hitBoundary)
    }

    private fun constrainScrollToContent(x: Float, y: Float): PointF {
        val constrainedX = MathUtils.clamp(x, minScrollX, maxScrollX)
        val constrainedY = MathUtils.clamp(y, minScrollY, maxScrollY)
        return PointF(constrainedX, constrainedY)
    }

    private fun updateScrollBounds() {
        val scaledContentWidth = contentWidth * currentZoom
        val scaledContentHeight = contentHeight * currentZoom

        // Calculate scroll bounds
        minScrollX = 0f
        maxScrollX = kotlin.math.max(0f, scaledContentWidth - viewportWidth)
        minScrollY = 0f
        maxScrollY = kotlin.math.max(0f, scaledContentHeight - viewportHeight)
    }

    private fun constrainScrollPosition() {
        val constrained = constrainScrollToContent(scrollX, scrollY)
        scrollX = constrained.x
        scrollY = constrained.y
        currentOverScrollX = 0f
        currentOverScrollY = 0f
    }

    private fun easeOutCubic(t: Float): Float {
        val shifted = t - 1f
        return 1f + shifted * shifted * shifted
    }

    /**
     * Gets current scroll position
     */
    fun getCurrentScroll(): PointF = PointF(scrollX, scrollY)

    /**
     * Gets scroll bounds
     */
    fun getScrollBounds(): ScrollBounds {
        return ScrollBounds(minScrollX, minScrollY, maxScrollX, maxScrollY)
    }

    /**
     * Gets current over-scroll
     */
    fun getCurrentOverScroll(): PointF = PointF(currentOverScrollX, currentOverScrollY)

    /**
     * Checks if can scroll in direction
     */
    fun canScrollHorizontally(direction: Int): Boolean {
        return if (direction < 0) scrollX > minScrollX else scrollX < maxScrollX
    }

    fun canScrollVertically(direction: Int): Boolean {
        return if (direction < 0) scrollY > minScrollY else scrollY < maxScrollY
    }

    /**
     * Gets scroll percentage (0-1)
     */
    fun getScrollPercentage(): PointF {
        val percentX = if (maxScrollX > minScrollX) {
            (scrollX - minScrollX) / (maxScrollX - minScrollX)
        } else 0f

        val percentY = if (maxScrollY > minScrollY) {
            (scrollY - minScrollY) / (maxScrollY - minScrollY)
        } else 0f

        return PointF(percentX, percentY)
    }

    /**
     * Sets over-scroll enabled state
     */
    fun setOverScrollEnabled(enabled: Boolean) {
        overScrollEnabled = enabled
        if (!enabled) {
            currentOverScrollX = 0f
            currentOverScrollY = 0f
            constrainScrollPosition()
        }
    }

    /**
     * Updates configuration
     */
    fun updateConfiguration(newConfig: Configuration) {
        configuration = newConfig
    }

    /**
     * Resets scroll to origin
     */
    fun reset(): ScrollResult {
        return setScroll(0f, 0f)
    }

    // Data classes
    data class ScrollBoundsResult(
        val newScrollX: Float,
        val newScrollY: Float,
        val overScrollX: Float,
        val overScrollY: Float,
        val hitBoundary: Boolean
    )

    data class ScrollBounds(
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float
    ) {
        fun contains(x: Float, y: Float): Boolean {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
        }

        fun getWidth(): Float = maxX - minX
        fun getHeight(): Float = maxY - minY
    }

    data class ScrollAnimation(
        val startX: Float,
        val startY: Float,
        val targetX: Float,
        val targetY: Float,
        val duration: Long,
        val startTime: Long
    ) {
        fun isComplete(): Boolean {
            return System.currentTimeMillis() - startTime >= duration
        }

        fun getProgress(): Float {
            val elapsed = System.currentTimeMillis() - startTime
            return MathUtils.clamp(elapsed.toFloat() / duration, 0f, 1f)
        }
    }

    /**
     * Gets scroll handler statistics
     */
    fun getStatistics(): ScrollStatistics {
        val bounds = getScrollBounds()
        val percentage = getScrollPercentage()

        return ScrollStatistics(
            currentScroll = getCurrentScroll(),
            scrollBounds = bounds,
            scrollPercentage = percentage,
            overScroll = getCurrentOverScroll(),
            canScrollLeft = canScrollHorizontally(-1),
            canScrollRight = canScrollHorizontally(1),
            canScrollUp = canScrollVertically(-1),
            canScrollDown = canScrollVertically(1),
            overScrollEnabled = overScrollEnabled,
            contentSize = PointF(contentWidth, contentHeight),
            viewportSize = PointF(viewportWidth, viewportHeight),
            currentZoom = currentZoom
        )
    }

    data class ScrollStatistics(
        val currentScroll: PointF,
        val scrollBounds: ScrollBounds,
        val scrollPercentage: PointF,
        val overScroll: PointF,
        val canScrollLeft: Boolean,
        val canScrollRight: Boolean,
        val canScrollUp: Boolean,
        val canScrollDown: Boolean,
        val overScrollEnabled: Boolean,
        val contentSize: PointF,
        val viewportSize: PointF,
        val currentZoom: Float
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Scroll Handler Statistics:")
                appendLine("  Position: (${currentScroll.x.toInt()}, ${currentScroll.y.toInt()})")
                appendLine("  Bounds: (${scrollBounds.minX.toInt()}, ${scrollBounds.minY.toInt()}) to (${scrollBounds.maxX.toInt()}, ${scrollBounds.maxY.toInt()})")
                appendLine("  Percentage: (${(scrollPercentage.x * 100).toInt()}%, ${(scrollPercentage.y * 100).toInt()}%)")
                appendLine("  Over-scroll: (${overScroll.x.toInt()}, ${overScroll.y.toInt()})")
                appendLine("  Can Scroll: L=$canScrollLeft, R=$canScrollRight, U=$canScrollUp, D=$canScrollDown")
                appendLine("  Over-scroll Enabled: $overScrollEnabled")
                appendLine("  Content: ${contentSize.x.toInt()}x${contentSize.y.toInt()}")
                appendLine("  Viewport: ${viewportSize.x.toInt()}x${viewportSize.y.toInt()}")
                appendLine("  Zoom: ${String.format("%.2f", currentZoom)}")
            }
        }
    }
}