package com.sonpxp.pdfloader.model

import com.sonpxp.pdfloader.DocumentLoadState
import com.sonpxp.pdfloader.FitPolicy
import com.sonpxp.pdfloader.PageScrollState
import com.sonpxp.pdfloader.ScrollDirection
import android.graphics.PointF
import android.graphics.RectF
import com.sonpxp.pdfloader.RenderQuality

/**
 * Represents the current state of the PDF viewer
 * Contains all information about what's currently displayed and how
 */
data class ViewState(
    /** Current page number (0-indexed) */
    val currentPage: Int = 0,

    /** Total number of pages */
    val totalPages: Int = 0,

    /** Current zoom level (1.0 = 100%) */
    val zoomLevel: Float = 1.0f,

    /** Minimum allowed zoom level */
    val minZoom: Float = 0.5f,

    /** Maximum allowed zoom level */
    val maxZoom: Float = 5.0f,

    /** Current scroll position */
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,

    /** View dimensions */
    val viewWidth: Int = 0,
    val viewHeight: Int = 0,

    /** Current page fit policy */
    val fitPolicy: FitPolicy = FitPolicy.WIDTH,

    /** Current scroll direction */
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,

    /** Whether the view is currently animating */
    val isAnimating: Boolean = false,

    /** Current animation state */
    val animationState: AnimationState = AnimationState.IDLE,

    /** Current zoom state */
    val zoomState: ZoomState = ZoomState.IDLE,

    /** Current page scroll state */
    val pageScrollState: PageScrollState = PageScrollState.IDLE,

    /** Document load state */
    val loadState: DocumentLoadState = DocumentLoadState.NOT_LOADED,

    /** Visible pages with their visibility info */
    val visiblePages: Map<Int, PageVisibility> = emptyMap(),

    /** Visible bounds of the current page */
    val visibleBounds: RectF = RectF(),

    /** Whether night mode is enabled */
    val isNightMode: Boolean = false,

    /** Whether the user is currently interacting */
    val isUserInteracting: Boolean = false,

    /** Last user interaction timestamp */
    val lastInteractionTime: Long = 0,

    /** Current render quality */
    val renderQuality: RenderQuality = RenderQuality.MEDIUM,

    /** Whether low memory mode is active */
    val isLowMemoryMode: Boolean = false,

    /** Additional view properties */
    val properties: Map<String, Any> = emptyMap()
) {

    /**
     * Checks if the document is loaded and ready
     */
    fun isDocumentReady(): Boolean {
        return loadState == DocumentLoadState.LOADED && totalPages > 0
    }

    /**
     * Checks if the current page is valid
     */
    fun isCurrentPageValid(): Boolean {
        return currentPage >= 0 && currentPage < totalPages
    }

    /**
     * Gets the zoom percentage (100% = normal size)
     */
    fun getZoomPercentage(): Int {
        return (zoomLevel * 100).toInt()
    }

    /**
     * Checks if zoom is at minimum level
     */
    fun isAtMinZoom(): Boolean {
        return zoomLevel <= minZoom + 0.01f
    }

    /**
     * Checks if zoom is at maximum level
     */
    fun isAtMaxZoom(): Boolean {
        return zoomLevel >= maxZoom - 0.01f
    }

    /**
     * Gets the current page progress (0.0 to 1.0)
     */
    fun getCurrentPageProgress(): Float {
        return if (totalPages > 0) currentPage.toFloat() / totalPages.toFloat() else 0f
    }

    /**
     * Gets the primary visible page (the most visible one)
     */
    fun getPrimaryVisiblePage(): Int? {
        return visiblePages.entries
            .firstOrNull { it.value == PageVisibility.PRIMARY }
            ?.key
    }

    /**
     * Gets all visible page numbers
     */
    fun getVisiblePageNumbers(): List<Int> {
        return visiblePages.keys.toList().sorted()
    }

    /**
     * Checks if a specific page is visible
     */
    fun isPageVisible(pageNumber: Int): Boolean {
        return visiblePages.containsKey(pageNumber) &&
                visiblePages[pageNumber] != PageVisibility.HIDDEN
    }

    /**
     * Gets the current scroll position as a point
     */
    fun getScrollPosition(): PointF {
        return PointF(scrollX, scrollY)
    }

    /**
     * Gets the view size as a point
     */
    fun getViewSize(): PointF {
        return PointF(viewWidth.toFloat(), viewHeight.toFloat())
    }

    /**
     * Checks if the view can scroll horizontally
     */
    fun canScrollHorizontally(): Boolean {
        return scrollDirection == ScrollDirection.HORIZONTAL ||
                scrollDirection == ScrollDirection.BOTH
    }

    /**
     * Checks if the view can scroll vertically
     */
    fun canScrollVertically(): Boolean {
        return scrollDirection == ScrollDirection.VERTICAL ||
                scrollDirection == ScrollDirection.BOTH
    }

    /**
     * Creates a copy with a new current page
     */
    fun withCurrentPage(pageNumber: Int): ViewState {
        return copy(currentPage = pageNumber.coerceIn(0, totalPages - 1))
    }

    /**
     * Creates a copy with a new zoom level
     */
    fun withZoomLevel(zoom: Float): ViewState {
        return copy(zoomLevel = zoom.coerceIn(minZoom, maxZoom))
    }

    /**
     * Creates a copy with a new scroll position
     */
    fun withScrollPosition(x: Float, y: Float): ViewState {
        return copy(scrollX = x, scrollY = y)
    }

    /**
     * Creates a copy with updated visible pages
     */
    fun withVisiblePages(pages: Map<Int, PageVisibility>): ViewState {
        return copy(visiblePages = pages)
    }

    /**
     * Creates a copy with updated animation state
     */
    fun withAnimationState(
        animating: Boolean,
        animationState: AnimationState = if (animating) AnimationState.RUNNING else AnimationState.IDLE
    ): ViewState {
        return copy(isAnimating = animating, animationState = animationState)
    }

    /**
     * Creates a copy with updated zoom state
     */
    fun withZoomState(newZoomState: ZoomState): ViewState {
        return copy(zoomState = newZoomState)
    }

    /**
     * Creates a copy with updated user interaction state
     */
    fun withUserInteraction(interacting: Boolean): ViewState {
        return copy(
            isUserInteracting = interacting,
            lastInteractionTime = if (interacting) System.currentTimeMillis() else lastInteractionTime
        )
    }

    /**
     * Creates a copy with updated document load state
     */
    fun withLoadState(newLoadState: DocumentLoadState, pageCount: Int = totalPages): ViewState {
        return copy(loadState = newLoadState, totalPages = pageCount)
    }

    /**
     * Creates a copy with updated view dimensions
     */
    fun withViewDimensions(width: Int, height: Int): ViewState {
        return copy(viewWidth = width, viewHeight = height)
    }

    /**
     * Gets a summary of the current view state
     */
    fun getSummary(): String {
        val pageInfo = if (isDocumentReady()) {
            "Page ${currentPage + 1}/$totalPages"
        } else {
            "No document"
        }

        val zoomInfo = "${getZoomPercentage()}%"
        val scrollInfo = "(${scrollX.toInt()}, ${scrollY.toInt()})"

        return "$pageInfo, Zoom: $zoomInfo, Scroll: $scrollInfo"
    }

    /**
     * Gets detailed view state information for debugging
     */
    fun getDetailedInfo(): String {
        return buildString {
            appendLine("View State:")
            appendLine("  Document: ${if (isDocumentReady()) "Ready" else loadState}")
            appendLine("  Current Page: ${currentPage + 1}/$totalPages")
            appendLine("  Zoom: ${String.format("%.2f", zoomLevel)} (${getZoomPercentage()}%)")
            appendLine("  Zoom Range: ${String.format("%.2f", minZoom)} - ${String.format("%.2f", maxZoom)}")
            appendLine("  Scroll: (${String.format("%.1f", scrollX)}, ${String.format("%.1f", scrollY)})")
            appendLine("  View Size: ${viewWidth}x${viewHeight}")
            appendLine("  Fit Policy: $fitPolicy")
            appendLine("  Scroll Direction: $scrollDirection")
            appendLine("  States: Animation=$animationState, Zoom=$zoomState, PageScroll=$pageScrollState")
            appendLine("  Visible Pages: ${getVisiblePageNumbers()}")
            appendLine("  Night Mode: $isNightMode")
            appendLine("  Low Memory Mode: $isLowMemoryMode")
            appendLine("  User Interacting: $isUserInteracting")
            appendLine("  Render Quality: $renderQuality")

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
         * Creates an initial view state
         */
        fun initial(): ViewState {
            return ViewState()
        }

        /**
         * Creates a view state for a loaded document
         */
        fun forDocument(pageCount: Int, viewWidth: Int = 0, viewHeight: Int = 0): ViewState {
            return ViewState(
                totalPages = pageCount,
                loadState = DocumentLoadState.LOADED,
                viewWidth = viewWidth,
                viewHeight = viewHeight
            )
        }

        /**
         * Creates a view state with error
         */
        fun withError(): ViewState {
            return ViewState(loadState = DocumentLoadState.ERROR)
        }

        /**
         * Creates a view state for loading
         */
        fun loading(): ViewState {
            return ViewState(loadState = DocumentLoadState.LOADING)
        }
    }
}