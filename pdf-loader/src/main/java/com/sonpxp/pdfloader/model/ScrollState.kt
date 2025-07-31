package com.sonpxp.pdfloader.model

import com.sonpxp.pdfloader.ScrollDirection
import android.graphics.PointF
import com.sonpxp.pdfloader.PageScrollState

/**
 * Represents the current scroll state of the PDF viewer
 * Contains information about scroll position, velocity, and constraints
 */
data class ScrollState(
    /** Current scroll position */
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,

    /** Previous scroll position (for delta calculations) */
    val previousScrollX: Float = 0f,
    val previousScrollY: Float = 0f,

    /** Current scroll velocity in pixels per second */
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,

    /** Scroll bounds (min/max scroll positions) */
    val minScrollX: Float = 0f,
    val maxScrollX: Float = 0f,
    val minScrollY: Float = 0f,
    val maxScrollY: Float = 0f,

    /** Content size (total scrollable area) */
    val contentWidth: Float = 0f,
    val contentHeight: Float = 0f,

    /** Viewport size (visible area) */
    val viewportWidth: Float = 0f,
    val viewportHeight: Float = 0f,

    /** Current scroll state */
    val pageScrollState: PageScrollState = PageScrollState.IDLE,

    /** Whether scroll is currently being animated */
    val isAnimatingScroll: Boolean = false,

    /** Whether user is currently dragging/scrolling */
    val isUserScrolling: Boolean = false,

    /** Whether scroll has reached boundaries */
    val isAtLeftBound: Boolean = false,
    val isAtRightBound: Boolean = false,
    val isAtTopBound: Boolean = false,
    val isAtBottomBound: Boolean = false,

    /** Scroll direction for layout */
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,

    /** Whether scroll snapping is enabled */
    val snapEnabled: Boolean = false,

    /** Snap threshold (0.0 to 1.0) */
    val snapThreshold: Float = 0.5f,

    /** Last scroll update timestamp */
    val lastUpdateTime: Long = System.currentTimeMillis(),

    /** Additional scroll properties */
    val properties: Map<String, Any> = emptyMap()
) {

    /**
     * Gets the current scroll position as a point
     */
    fun getScrollPosition(): PointF {
        return PointF(scrollX, scrollY)
    }

    /**
     * Gets the previous scroll position as a point
     */
    fun getPreviousScrollPosition(): PointF {
        return PointF(previousScrollX, previousScrollY)
    }

    /**
     * Gets the scroll delta since last update
     */
    fun getScrollDelta(): PointF {
        return PointF(scrollX - previousScrollX, scrollY - previousScrollY)
    }

    /**
     * Gets the current velocity as a point
     */
    fun getVelocity(): PointF {
        return PointF(velocityX, velocityY)
    }

    /**
     * Gets the total velocity magnitude
     */
    fun getVelocityMagnitude(): Float {
        return kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)
    }

    /**
     * Gets the scroll progress horizontally (0.0 to 1.0)
     */
    fun getHorizontalScrollProgress(): Float {
        val scrollRange = maxScrollX - minScrollX
        return if (scrollRange > 0) {
            ((scrollX - minScrollX) / scrollRange).coerceIn(0f, 1f)
        } else 0f
    }

    /**
     * Gets the scroll progress vertically (0.0 to 1.0)
     */
    fun getVerticalScrollProgress(): Float {
        val scrollRange = maxScrollY - minScrollY
        return if (scrollRange > 0) {
            ((scrollY - minScrollY) / scrollRange).coerceIn(0f, 1f)
        } else 0f
    }

    /**
     * Checks if content can scroll horizontally
     */
    fun canScrollHorizontally(): Boolean {
        return maxScrollX > minScrollX &&
                (scrollDirection == ScrollDirection.HORIZONTAL || scrollDirection == ScrollDirection.BOTH)
    }

    /**
     * Checks if content can scroll vertically
     */
    fun canScrollVertically(): Boolean {
        return maxScrollY > minScrollY &&
                (scrollDirection == ScrollDirection.VERTICAL || scrollDirection == ScrollDirection.BOTH)
    }

    /**
     * Checks if the scroll position is within bounds
     */
    fun isWithinBounds(): Boolean {
        return scrollX >= minScrollX && scrollX <= maxScrollX &&
                scrollY >= minScrollY && scrollY <= maxScrollY
    }

    /**
     * Gets the constrained scroll position (within bounds)
     */
    fun getConstrainedPosition(): PointF {
        return PointF(
            scrollX.coerceIn(minScrollX, maxScrollX),
            scrollY.coerceIn(minScrollY, maxScrollY)
        )
    }

    /**
     * Checks if currently at any boundary
     */
    fun isAtAnyBound(): Boolean {
        return isAtLeftBound || isAtRightBound || isAtTopBound || isAtBottomBound
    }

    /**
     * Gets which boundaries are currently reached
     */
    fun getReachedBounds(): List<ScrollBoundary> {
        val bounds = mutableListOf<ScrollBoundary>()
        if (isAtLeftBound) bounds.add(ScrollBoundary.LEFT)
        if (isAtRightBound) bounds.add(ScrollBoundary.RIGHT)
        if (isAtTopBound) bounds.add(ScrollBoundary.TOP)
        if (isAtBottomBound) bounds.add(ScrollBoundary.BOTTOM)
        return bounds
    }

    /**
     * Creates a copy with updated scroll position
     */
    fun withScrollPosition(newX: Float, newY: Float): ScrollState {
        return copy(
            previousScrollX = scrollX,
            previousScrollY = scrollY,
            scrollX = newX,
            scrollY = newY,
            lastUpdateTime = System.currentTimeMillis()
        ).updateBoundaryStates()
    }

    /**
     * Creates a copy with updated velocity
     */
    fun withVelocity(newVelocityX: Float, newVelocityY: Float): ScrollState {
        return copy(
            velocityX = newVelocityX,
            velocityY = newVelocityY,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Creates a copy with updated scroll bounds
     */
    fun withBounds(
        newMinX: Float, newMaxX: Float,
        newMinY: Float, newMaxY: Float
    ): ScrollState {
        return copy(
            minScrollX = newMinX,
            maxScrollX = newMaxX,
            minScrollY = newMinY,
            maxScrollY = newMaxY
        ).updateBoundaryStates()
    }

    /**
     * Creates a copy with updated content size
     */
    fun withContentSize(width: Float, height: Float): ScrollState {
        return copy(
            contentWidth = width,
            contentHeight = height
        )
    }

    /**
     * Creates a copy with updated viewport size
     */
    fun withViewportSize(width: Float, height: Float): ScrollState {
        return copy(
            viewportWidth = width,
            viewportHeight = height
        )
    }

    /**
     * Creates a copy with updated scroll state
     */
    fun withPageScrollState(newState: PageScrollState): ScrollState {
        return copy(pageScrollState = newState)
    }

    /**
     * Creates a copy with updated animation state
     */
    fun withAnimationState(animating: Boolean): ScrollState {
        return copy(isAnimatingScroll = animating)
    }

    /**
     * Creates a copy with updated user scrolling state
     */
    fun withUserScrollingState(scrolling: Boolean): ScrollState {
        return copy(isUserScrolling = scrolling)
    }

    /**
     * Creates a copy with snap settings
     */
    fun withSnapSettings(enabled: Boolean, threshold: Float = 0.5f): ScrollState {
        return copy(
            snapEnabled = enabled,
            snapThreshold = threshold.coerceIn(0f, 1f)
        )
    }

    /**
     * Updates boundary states based on current position
     */
    private fun updateBoundaryStates(): ScrollState {
        val tolerance = 1f // 1 pixel tolerance for boundary detection

        return copy(
            isAtLeftBound = scrollX <= minScrollX + tolerance,
            isAtRightBound = scrollX >= maxScrollX - tolerance,
            isAtTopBound = scrollY <= minScrollY + tolerance,
            isAtBottomBound = scrollY >= maxScrollY - tolerance
        )
    }

    /**
     * Gets the scroll position that would result from applying current velocity for a given time
     */
    fun predictScrollPosition(timeMs: Long): PointF {
        val timeSeconds = timeMs / 1000f
        val friction = 0.95f // Friction factor for velocity decay

        val predictedVelX = velocityX * kotlin.math.pow(friction, timeSeconds.toDouble()).toFloat()
        val predictedVelY = velocityY * kotlin.math.pow(friction, timeSeconds.toDouble()).toFloat()

        return PointF(
            (scrollX + predictedVelX * timeSeconds).coerceIn(minScrollX, maxScrollX),
            (scrollY + predictedVelY * timeSeconds).coerceIn(minScrollY, maxScrollY)
        )
    }

    /**
     * Gets the next snap position based on current scroll and velocity
     */
    fun getNextSnapPosition(pageWidth: Float, pageHeight: Float): PointF? {
        if (!snapEnabled) return null

        val snapX = when (scrollDirection) {
            ScrollDirection.HORIZONTAL, ScrollDirection.BOTH -> {
                val currentPage = (scrollX / pageWidth).toInt()
                val progress = (scrollX % pageWidth) / pageWidth
                val nextPage = if (velocityX > 50 || progress > snapThreshold) currentPage + 1 else currentPage
                (nextPage * pageWidth).coerceIn(minScrollX, maxScrollX)
            }
            else -> scrollX
        }

        val snapY = when (scrollDirection) {
            ScrollDirection.VERTICAL, ScrollDirection.BOTH -> {
                val currentPage = (scrollY / pageHeight).toInt()
                val progress = (scrollY % pageHeight) / pageHeight
                val nextPage = if (velocityY > 50 || progress > snapThreshold) currentPage + 1 else currentPage
                (nextPage * pageHeight).coerceIn(minScrollY, maxScrollY)
            }
            else -> scrollY
        }

        return PointF(snapX, snapY)
    }

    /**
     * Gets scroll state summary for debugging
     */
    fun getSummary(): String {
        return "Scroll: (${scrollX.toInt()}, ${scrollY.toInt()}), " +
                "Velocity: (${velocityX.toInt()}, ${velocityY.toInt()}), " +
                "State: $pageScrollState"
    }

    /**
     * Gets detailed scroll information for debugging
     */
    fun getDetailedInfo(): String {
        return buildString {
            appendLine("Scroll State:")
            appendLine("  Position: (${String.format("%.1f", scrollX)}, ${String.format("%.1f", scrollY)})")
            appendLine("  Previous: (${String.format("%.1f", previousScrollX)}, ${String.format("%.1f", previousScrollY)})")
            appendLine("  Delta: (${String.format("%.1f", scrollX - previousScrollX)}, ${String.format("%.1f", scrollY - previousScrollY)})")
            appendLine("  Velocity: (${String.format("%.1f", velocityX)}, ${String.format("%.1f", velocityY)}) px/s")
            appendLine("  Bounds: X=[${String.format("%.1f", minScrollX)}, ${String.format("%.1f", maxScrollX)}], Y=[${String.format("%.1f", minScrollY)}, ${String.format("%.1f", maxScrollY)}]")
            appendLine("  Content Size: ${String.format("%.1f", contentWidth)} x ${String.format("%.1f", contentHeight)}")
            appendLine("  Viewport Size: ${String.format("%.1f", viewportWidth)} x ${String.format("%.1f", viewportHeight)}")
            appendLine("  Progress: H=${String.format("%.1f", getHorizontalScrollProgress() * 100)}%, V=${String.format("%.1f", getVerticalScrollProgress() * 100)}%")
            appendLine("  State: $pageScrollState")
            appendLine("  Animating: $isAnimatingScroll")
            appendLine("  User Scrolling: $isUserScrolling")
            appendLine("  Direction: $scrollDirection")
            appendLine("  Snap: ${if (snapEnabled) "enabled (${snapThreshold})" else "disabled"}")
            appendLine("  Boundaries: ${getReachedBounds().joinToString()}")

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
         * Creates an initial scroll state
         */
        fun initial(
            scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
            snapEnabled: Boolean = false
        ): ScrollState {
            return ScrollState(
                scrollDirection = scrollDirection,
                snapEnabled = snapEnabled
            )
        }

        /**
         * Creates a scroll state with specified bounds
         */
        fun withBounds(
            minX: Float, maxX: Float,
            minY: Float, maxY: Float,
            scrollDirection: ScrollDirection = ScrollDirection.VERTICAL
        ): ScrollState {
            return ScrollState(
                minScrollX = minX,
                maxScrollX = maxX,
                minScrollY = minY,
                maxScrollY = maxY,
                scrollDirection = scrollDirection
            ).updateBoundaryStates()
        }

        /**
         * Creates a scroll state for a specific content and viewport size
         */
        fun forContent(
            contentWidth: Float,
            contentHeight: Float,
            viewportWidth: Float,
            viewportHeight: Float,
            scrollDirection: ScrollDirection = ScrollDirection.VERTICAL
        ): ScrollState {
            val maxScrollX = kotlin.math.max(0f, contentWidth - viewportWidth)
            val maxScrollY = kotlin.math.max(0f, contentHeight - viewportHeight)

            return ScrollState(
                contentWidth = contentWidth,
                contentHeight = contentHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                maxScrollX = maxScrollX,
                maxScrollY = maxScrollY,
                scrollDirection = scrollDirection
            ).updateBoundaryStates()
        }
    }
}

/**
 * Scroll boundary enumeration
 */
enum class ScrollBoundary {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
}