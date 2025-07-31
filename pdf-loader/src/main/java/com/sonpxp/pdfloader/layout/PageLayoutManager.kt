package com.sonpxp.pdfloader.layout

import android.graphics.PointF
import android.graphics.RectF
import com.sonpxp.pdfloader.FitPolicy
import com.sonpxp.pdfloader.model.Configuration
import com.sonpxp.pdfloader.model.PageInfo
import com.sonpxp.pdfloader.utils.MathUtils
import kotlin.math.min


/**
 * Manages the layout and positioning of PDF pages
 * Handles page arrangement, scaling, and coordinate transformations
 */
class PageLayoutManager(
    private val configuration: Configuration,
) {

    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var currentZoom = 1f
    private var scrollX = 0f
    private var scrollY = 0f

    private val pageInfoList = mutableListOf<PageInfo>()
    private var totalLayoutWidth = 0f
    private var totalLayoutHeight = 0f

    /**
     * Gets display density (stub - should be implemented with actual Android context)
     */
    private fun getDisplayDensity(): Float {
        return 3f // Assume xhdpi density
    }

    /**
     * Page layout result containing all calculated positions and sizes
     */
    data class LayoutResult(
        val pages: List<PageLayout>,
        val totalWidth: Float,
        val totalHeight: Float,
        val visiblePages: List<Int>,
        val currentPage: Int,
    )

    /**
     * Individual page layout information
     */
    data class PageLayout(
        val pageNumber: Int,
        val bounds: RectF,
        val scaledBounds: RectF,
        val scale: Float,
        val isVisible: Boolean,
        val visibilityPercentage: Float,
        val offset: PointF,
    )

    /**
     * Updates the viewport dimensions
     */
    fun setViewport(width: Float, height: Float) {
        if (viewportWidth != width || viewportHeight != height) {
            viewportWidth = width
            viewportHeight = height
            invalidateLayout()
        }
    }

    /**
     * Updates zoom level
     */
    fun setZoom(zoom: Float) {
        val clampedZoom = MathUtils.clamp(zoom, configuration.minZoom, configuration.maxZoom)
        if (currentZoom != clampedZoom) {
            currentZoom = clampedZoom
            invalidateLayout()
        }
    }

    /**
     * Updates scroll position
     */
    fun setScroll(x: Float, y: Float) {
        scrollX = x
        scrollY = y
    }

    /**
     * Sets page information for layout calculation
     */
    fun setPages(pages: List<PageInfo>) {
        pageInfoList.clear()
        pageInfoList.addAll(pages)
        invalidateLayout()
    }

    /**
     * Calculates the complete layout for all pages
     */
    fun calculateLayout(): LayoutResult {
        if (pageInfoList.isEmpty() || viewportWidth <= 0 || viewportHeight <= 0) {
            return LayoutResult(emptyList(), 0f, 0f, emptyList(), 0)
        }

        val pageLayouts = mutableListOf<PageLayout>()
        var currentY = 0f
        val spacing = if (configuration.autoSpacing) {
            calculateAutoSpacing()
        } else {
            configuration.spacingDp * getDisplayDensity()
        }

        // Calculate layout for each page
        for (pageInfo in pageInfoList) {
            val pageLayout = calculatePageLayout(pageInfo, currentY)
            pageLayouts.add(pageLayout)

            currentY = pageLayout.scaledBounds.bottom + spacing
        }

        // Calculate total dimensions
        totalLayoutWidth = pageLayouts.maxOfOrNull { it.scaledBounds.right } ?: 0f
        totalLayoutHeight = currentY - spacing // Remove last spacing

        // Determine visible pages and current page
        val visiblePages = pageLayouts.filter { it.isVisible }.map { it.pageNumber }
        val currentPage = determineCurrentPage(pageLayouts)

        return LayoutResult(
            pages = pageLayouts,
            totalWidth = totalLayoutWidth,
            totalHeight = totalLayoutHeight,
            visiblePages = visiblePages,
            currentPage = currentPage
        )
    }

    /**
     * Calculates layout for a single page
     */
    private fun calculatePageLayout(pageInfo: PageInfo, startY: Float): PageLayout {
        // Calculate base scale according to fit policy
        val baseScale = calculateFitScale(pageInfo)
        val finalScale = baseScale * currentZoom

        // Calculate scaled dimensions
        val scaledWidth = pageInfo.originalWidth * finalScale
        val scaledHeight = pageInfo.originalHeight * finalScale

        // Calculate position
        val x = when (configuration.pageFitPolicy) {
            FitPolicy.WIDTH -> 0f
            else -> (viewportWidth - scaledWidth) / 2f
        }

        // Create bounds
        val bounds = RectF(0f, 0f, pageInfo.originalWidth, pageInfo.originalHeight)
        val scaledBounds = RectF(x, startY, x + scaledWidth, startY + scaledHeight)

        // Calculate visibility
        val viewportRect =
            RectF(-scrollX, -scrollY, -scrollX + viewportWidth, -scrollY + viewportHeight)
        val intersection = RectF()
        val isVisible = intersection.setIntersect(scaledBounds, viewportRect)

        val visibilityPercentage = if (isVisible) {
            val intersectionArea = intersection.width() * intersection.height()
            val pageArea = scaledBounds.width() * scaledBounds.height()
            intersectionArea / pageArea
        } else {
            0f
        }

        return PageLayout(
            pageNumber = pageInfo.pageNumber,
            bounds = bounds,
            scaledBounds = scaledBounds,
            scale = finalScale,
            isVisible = isVisible,
            visibilityPercentage = visibilityPercentage,
            offset = PointF(x, startY)
        )
    }

    /**
     * Calculates fit scale for a page based on fit policy
     */
    private fun calculateFitScale(pageInfo: PageInfo): Float {
        return when (configuration.pageFitPolicy) {
            FitPolicy.WIDTH -> viewportWidth / pageInfo.originalWidth
            FitPolicy.HEIGHT -> viewportHeight / pageInfo.originalHeight
            FitPolicy.BOTH -> min(
                viewportWidth / pageInfo.originalWidth,
                viewportHeight / pageInfo.originalHeight
            )

            FitPolicy.AUTO -> {
                val widthScale = viewportWidth / pageInfo.originalWidth
                val heightScale = viewportHeight / pageInfo.originalHeight

                // Choose the scale that shows more content
                if (pageInfo.originalWidth > pageInfo.originalHeight) {
                    widthScale // Landscape - fit to width
                } else {
                    min(widthScale, heightScale) // Portrait - fit inside
                }
            }
        }
    }

    /**
     * Calculates automatic spacing between pages
     */
    private fun calculateAutoSpacing(): Float {
        val density = getDisplayDensity()
        val baseSpacing = 16f * density // 16dp base spacing

        return when {
            currentZoom < 1f -> baseSpacing * 0.5f // Less spacing when zoomed out
            currentZoom > 2f -> baseSpacing * 1.5f // More spacing when zoomed in
            else -> baseSpacing
        }
    }

    /**
     * Determines the current page based on visibility
     */
    private fun determineCurrentPage(pageLayouts: List<PageLayout>): Int {
        if (pageLayouts.isEmpty()) return 0

        // Find the page with highest visibility percentage that's mostly visible
        val mostVisiblePage = pageLayouts
            .filter { it.visibilityPercentage > 0.5f }
            .maxByOrNull { it.visibilityPercentage }
            ?: pageLayouts.firstOrNull { it.isVisible }
            ?: pageLayouts.first()

        return mostVisiblePage.pageNumber
    }

    /**
     * Gets the bounds for a specific page
     */
    fun getPageBounds(pageNumber: Int): RectF? {
        return calculateLayout().pages.find { it.pageNumber == pageNumber }?.scaledBounds
    }

    /**
     * Gets the scale for a specific page
     */
    fun getPageScale(pageNumber: Int): Float {
        return calculateLayout().pages.find { it.pageNumber == pageNumber }?.scale ?: 1f
    }

    /**
     * Converts screen coordinates to page coordinates
     */
    fun screenToPage(pageNumber: Int, screenX: Float, screenY: Float): PointF? {
        val pageLayout = calculateLayout().pages.find { it.pageNumber == pageNumber } ?: return null

        val pageX = (screenX + scrollX - pageLayout.offset.x) / pageLayout.scale
        val pageY = (screenY + scrollY - pageLayout.offset.y) / pageLayout.scale

        return PointF(pageX, pageY)
    }

    /**
     * Converts page coordinates to screen coordinates
     */
    fun pageToScreen(pageNumber: Int, pageX: Float, pageY: Float): PointF? {
        val pageLayout = calculateLayout().pages.find { it.pageNumber == pageNumber } ?: return null

        val screenX = pageX * pageLayout.scale + pageLayout.offset.x - scrollX
        val screenY = pageY * pageLayout.scale + pageLayout.offset.y - scrollY

        return PointF(screenX, screenY)
    }

    /**
     * Gets the scroll position to center a specific page
     */
    fun getScrollToCenter(pageNumber: Int): PointF? {
        val pageLayout = calculateLayout().pages.find { it.pageNumber == pageNumber } ?: return null

        val centerX = pageLayout.scaledBounds.centerX() - viewportWidth / 2f
        val centerY = pageLayout.scaledBounds.centerY() - viewportHeight / 2f

        return PointF(centerX, centerY)
    }

    /**
     * Gets the scroll position to show a specific page at the top
     */
    fun getScrollToTop(pageNumber: Int): PointF? {
        val pageLayout = calculateLayout().pages.find { it.pageNumber == pageNumber } ?: return null

        val centerX = pageLayout.scaledBounds.centerX() - viewportWidth / 2f
        val topY = pageLayout.scaledBounds.top

        return PointF(centerX, topY)
    }

    /**
     * Calculates zoom level to fit a specific page
     */
    fun calculateZoomToFitPage(pageNumber: Int): Float {
        val pageInfo = pageInfoList.find { it.pageNumber == pageNumber } ?: return 1f

        val baseScale = calculateFitScale(pageInfo)
        return MathUtils.clamp(baseScale, configuration.minZoom, configuration.maxZoom)
    }

    /**
     * Calculates zoom level to fit page width
     */
    fun calculateZoomToFitWidth(pageNumber: Int): Float {
        val pageInfo = pageInfoList.find { it.pageNumber == pageNumber } ?: return 1f

        val widthScale = viewportWidth / pageInfo.originalWidth
        return MathUtils.clamp(widthScale, configuration.minZoom, configuration.maxZoom)
    }

    /**
     * Gets the current viewport width
     */
    fun getViewportWidth(): Float = viewportWidth

    /**
     * Gets the current viewport height
     */
    fun getViewportHeight(): Float = viewportHeight

    /**
     * Gets the current zoom level
     */
    fun getCurrentZoom(): Float = currentZoom

    /**
     * Gets the current scroll position
     */
    fun getCurrentScroll(): PointF = PointF(scrollX, scrollY)

    /**
     * Invalidates the current layout (triggers recalculation)
     */
    private fun invalidateLayout() {
        // Layout will be recalculated on next calculateLayout() call
    }

    /**
     * Gets layout statistics for debugging
     */
    fun getLayoutStatistics(): LayoutStatistics {
        val layout = calculateLayout()

        return LayoutStatistics(
            totalPages = pageInfoList.size,
            visiblePages = layout.visiblePages.size,
            currentPage = layout.currentPage,
            totalWidth = layout.totalWidth,
            totalHeight = layout.totalHeight,
            currentZoom = currentZoom,
            viewportSize = PointF(viewportWidth, viewportHeight),
            scrollPosition = PointF(scrollX, scrollY)
        )
    }

    data class LayoutStatistics(
        val totalPages: Int,
        val visiblePages: Int,
        val currentPage: Int,
        val totalWidth: Float,
        val totalHeight: Float,
        val currentZoom: Float,
        val viewportSize: PointF,
        val scrollPosition: PointF,
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Layout Statistics:")
                appendLine("  Pages: $visiblePages/$totalPages visible, current: $currentPage")
                appendLine("  Dimensions: ${totalWidth.toInt()}x${totalHeight.toInt()}")
                appendLine("  Viewport: ${viewportSize.x.toInt()}x${viewportSize.y.toInt()}")
                appendLine("  Zoom: ${String.format("%.2f", currentZoom)}")
                appendLine("  Scroll: (${scrollPosition.x.toInt()}, ${scrollPosition.y.toInt()})")
            }
        }
    }
}