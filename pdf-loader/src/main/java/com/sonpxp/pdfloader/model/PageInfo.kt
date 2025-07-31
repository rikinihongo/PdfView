package com.sonpxp.pdfloader.model

import android.graphics.RectF
import com.sonpxp.pdfloader.PageRenderState
import com.sonpxp.pdfloader.RenderQuality
import kotlin.math.abs

/**
 * Contains comprehensive information about a PDF page
 * This class tracks all aspects of a page including dimensions, rendering state,
 * visibility, content types, and performance metrics
 */
data class PageInfo(
    /** Page number (0-indexed) */
    val pageNumber: Int,

    /** Original page width in PDF points (1 point = 1/72 inch) */
    val originalWidth: Float,

    /** Original page height in PDF points */
    val originalHeight: Float,

    /** Current rendered width in screen pixels */
    val renderedWidth: Float,

    /** Current rendered height in screen pixels */
    val renderedHeight: Float,

    /** Current zoom level applied to this page (1.0 = 100%) */
    val zoomLevel: Float,

    /** Page rotation in degrees (0, 90, 180, 270) */
    val rotation: Int = 0,

    /** Current render state of this page */
    val renderState: PageRenderState = PageRenderState.NOT_RENDERED,

    /** Visible portion of the page on screen (in screen coordinates) */
    val visibleBounds: RectF = RectF(),

    /** Full bounds of the page (in screen coordinates) */
    val fullBounds: RectF = RectF(),

    /** Whether this page contains selectable text */
    val hasText: Boolean = true,

    /** Whether this page contains images */
    val hasImages: Boolean = false,

    /** Whether this page contains annotations/forms */
    val hasAnnotations: Boolean = false,

    /** Whether this page contains internal links */
    val hasLinks: Boolean = false,

    /** Timestamp when this page was last rendered */
    val lastRenderTime: Long = 0,

    /** Render quality used for this page */
    val renderQuality: RenderQuality = RenderQuality.MEDIUM,

    /** Size of the rendered bitmap in bytes (for memory tracking) */
    val bitmapSize: Long = 0,

    /** Whether this page is currently being rendered */
    val isRendering: Boolean = false,

    /** Error that occurred during rendering (if any) */
    val renderError: Throwable? = null,

    /** Custom metadata associated with this page */
    val metadata: Map<String, Any> = emptyMap()
) {

    /**
     * Gets the aspect ratio of the original page (width/height)
     */
    fun getAspectRatio(): Float {
        return if (originalHeight != 0f) originalWidth / originalHeight else 1f
    }

    /**
     * Gets the aspect ratio of the rendered page
     */
    fun getRenderedAspectRatio(): Float {
        return if (renderedHeight != 0f) renderedWidth / renderedHeight else 1f
    }

    /**
     * Checks if the page is currently visible on screen
     */
    fun isVisible(): Boolean {
        return !visibleBounds.isEmpty && getVisibilityPercentage() > 0f
    }

    /**
     * Gets the percentage of the page that is currently visible (0.0 to 1.0)
     */
    fun getVisibilityPercentage(): Float {
        if (fullBounds.isEmpty || visibleBounds.isEmpty) return 0f

        val intersect = RectF()
        if (!intersect.setIntersect(visibleBounds, fullBounds)) return 0f

        val visibleArea = intersect.width() * intersect.height()
        val totalArea = fullBounds.width() * fullBounds.height()

        return if (totalArea > 0) (visibleArea / totalArea).coerceIn(0f, 1f) else 0f
    }

    /**
     * Checks if the page is fully visible (at least 95% visible to account for rounding)
     */
    fun isFullyVisible(): Boolean {
        return getVisibilityPercentage() >= 0.95f
    }

    /**
     * Checks if the page is mostly visible (at least 50% visible)
     */
    fun isMostlyVisible(): Boolean {
        return getVisibilityPercentage() >= 0.5f
    }

    /**
     * Checks if the page is rendered and ready for display
     */
    fun isReadyForDisplay(): Boolean {
        return renderState == PageRenderState.RENDERED && renderError == null
    }

    /**
     * Checks if the page has an error
     */
    fun hasError(): Boolean {
        return renderState == PageRenderState.ERROR || renderError != null
    }

    /**
     * Checks if the page needs re-rendering due to zoom changes
     * @param currentZoom the current zoom level
     * @param qualityThreshold threshold for zoom difference (default 10%)
     */
    fun needsRerender(currentZoom: Float, qualityThreshold: Float = 0.1f): Boolean {
        val zoomDifference = abs(zoomLevel - currentZoom)
        return zoomDifference > qualityThreshold ||
                renderState == PageRenderState.ERROR ||
                renderState == PageRenderState.NOT_RENDERED
    }

    /**
     * Checks if the page should be preloaded based on visibility and position
     * @param currentPage the currently displayed page
     * @param preloadDistance how many pages away to preload
     */
    fun shouldPreload(currentPage: Int, preloadDistance: Int = 1): Boolean {
        val distance = abs(pageNumber - currentPage)
        return distance <= preloadDistance
    }

    /**
     * Gets the memory usage of this page in MB
     */
    fun getMemoryUsageMB(): Float {
        return bitmapSize / (1024f * 1024f)
    }

    /**
     * Gets the render time age in milliseconds
     */
    fun getRenderAge(): Long {
        return if (lastRenderTime > 0) System.currentTimeMillis() - lastRenderTime else Long.MAX_VALUE
    }

    /**
     * Checks if the rendered content is stale and should be refreshed
     * @param maxAge maximum age in milliseconds before considering stale
     */
    fun isRenderStale(maxAge: Long = 30_000): Boolean { // 30 seconds default
        return getRenderAge() > maxAge
    }

    /**
     * Creates a copy with updated render information
     */
    fun withRenderInfo(
        newRenderedWidth: Float,
        newRenderedHeight: Float,
        newZoomLevel: Float,
        newRenderState: PageRenderState = PageRenderState.RENDERED,
        newRenderTime: Long = System.currentTimeMillis(),
        newBitmapSize: Long = bitmapSize,
        newRenderError: Throwable? = null
    ): PageInfo {
        return copy(
            renderedWidth = newRenderedWidth,
            renderedHeight = newRenderedHeight,
            zoomLevel = newZoomLevel,
            renderState = newRenderState,
            lastRenderTime = newRenderTime,
            bitmapSize = newBitmapSize,
            renderError = newRenderError,
            isRendering = false
        )
    }

    /**
     * Creates a copy with updated bounds information
     */
    fun withBounds(newVisibleBounds: RectF, newFullBounds: RectF): PageInfo {
        return copy(
            visibleBounds = RectF(newVisibleBounds),
            fullBounds = RectF(newFullBounds)
        )
    }

    /**
     * Creates a copy marking the page as currently rendering
     */
    fun withRenderingState(): PageInfo {
        return copy(
            renderState = PageRenderState.RENDERING,
            isRendering = true,
            renderError = null
        )
    }

    /**
     * Creates a copy with an error state
     */
    fun withError(error: Throwable): PageInfo {
        return copy(
            renderState = PageRenderState.ERROR,
            isRendering = false,
            renderError = error
        )
    }

    /**
     * Creates a copy with updated content flags
     */
    fun withContentInfo(
        hasText: Boolean = this.hasText,
        hasImages: Boolean = this.hasImages,
        hasAnnotations: Boolean = this.hasAnnotations,
        hasLinks: Boolean = this.hasLinks
    ): PageInfo {
        return copy(
            hasText = hasText,
            hasImages = hasImages,
            hasAnnotations = hasAnnotations,
            hasLinks = hasLinks
        )
    }

    /**
     * Creates a copy with additional metadata
     */
    fun withMetadata(key: String, value: Any): PageInfo {
        val newMetadata = metadata.toMutableMap()
        newMetadata[key] = value
        return copy(metadata = newMetadata)
    }

    /**
     * Creates a copy with multiple metadata entries
     */
    fun withMetadata(additionalMetadata: Map<String, Any>): PageInfo {
        val newMetadata = metadata.toMutableMap()
        newMetadata.putAll(additionalMetadata)
        return copy(metadata = newMetadata)
    }

    /**
     * Gets metadata value by key
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMetadata(key: String): T? {
        return metadata[key] as? T
    }

    /**
     * Gets metadata value by key with default
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMetadata(key: String, defaultValue: T): T {
        return metadata[key] as? T ?: defaultValue
    }

    /**
     * Gets a human-readable description of the page
     */
    fun getDescription(): String {
        val sizeStr = "${originalWidth.toInt()}x${originalHeight.toInt()}pt"
        val zoomStr = String.format("%.1f", zoomLevel * 100)
        val stateStr = renderState.name.lowercase().capitalize()

        return "Page ${pageNumber + 1}: $sizeStr, ${zoomStr}% zoom, $stateStr"
    }

    /**
     * Gets detailed debug information about the page
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("Page ${pageNumber + 1} Debug Info:")
            appendLine("  Original: ${originalWidth.toInt()}x${originalHeight.toInt()}pt")
            appendLine("  Rendered: ${renderedWidth.toInt()}x${renderedHeight.toInt()}px")
            appendLine("  Zoom: ${String.format("%.2f", zoomLevel)} (${String.format("%.1f", zoomLevel * 100)}%)")
            appendLine("  Rotation: ${rotation}°")
            appendLine("  State: $renderState")
            appendLine("  Visible: ${String.format("%.1f", getVisibilityPercentage() * 100)}%")
            appendLine("  Memory: ${String.format("%.2f", getMemoryUsageMB())} MB")
            appendLine("  Content: text=$hasText, images=$hasImages, annotations=$hasAnnotations, links=$hasLinks")
            appendLine("  Quality: $renderQuality")
            if (renderError != null) {
                appendLine("  Error: ${renderError!!.message}")
            }
            if (metadata.isNotEmpty()) {
                appendLine("  Metadata: $metadata")
            }
        }
    }

    /**
     * Converts to a compact string representation for logging
     */
    fun toCompactString(): String {
        return "Page(${pageNumber + 1}, ${zoomLevel}x, $renderState, ${String.format("%.0f", getVisibilityPercentage() * 100)}% visible)"
    }

    override fun toString(): String {
        return getDescription()
    }
}