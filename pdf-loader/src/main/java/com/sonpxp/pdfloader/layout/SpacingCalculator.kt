package com.sonpxp.pdfloader.layout

import com.sonpxp.pdfloader.ScrollDirection
import com.sonpxp.pdfloader.model.Configuration
import com.sonpxp.pdfloader.model.PageInfo
import kotlin.collections.isNotEmpty
import kotlin.math.*

/**
 * Calculates spacing between PDF pages based on various factors
 * Handles adaptive spacing, manual spacing, and responsive layouts
 */
class SpacingCalculator(
    private val configuration: Configuration
) {

    /**
     * Spacing calculation result
     */
    data class SpacingResult(
        val topSpacing: Float,
        val bottomSpacing: Float,
        val leftSpacing: Float,
        val rightSpacing: Float,
        val interPageSpacing: Float,
        val adaptiveSpacing: Boolean
    ) {
        /**
         * Gets total vertical spacing
         */
        fun getTotalVerticalSpacing(): Float = topSpacing + bottomSpacing + interPageSpacing

        /**
         * Gets total horizontal spacing
         */
        fun getTotalHorizontalSpacing(): Float = leftSpacing + rightSpacing

        /**
         * Gets spacing for a specific direction
         */
        fun getSpacingForDirection(direction: ScrollDirection): Float {
            return when (direction) {
                ScrollDirection.VERTICAL -> interPageSpacing
                ScrollDirection.HORIZONTAL -> interPageSpacing
                ScrollDirection.BOTH -> maxOf(interPageSpacing, 8f)
            }
        }
    }

    /**
     * Spacing configuration parameters
     */
    data class SpacingConfig(
        val baseSpacingDp: Float = 16f,
        val minSpacingDp: Float = 4f,
        val maxSpacingDp: Float = 64f,
        val zoomSpacingFactor: Float = 1.2f,
        val aspectRatioThreshold: Float = 0.1f,
        val contentAwareSpacing: Boolean = true,
        val responsiveSpacing: Boolean = true
    )

    private val spacingConfig = SpacingConfig()
    private var displayDensity = 3f // Will be set from context
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var currentZoom = 1f

    /**
     * Sets display parameters
     */
    fun setDisplayParameters(density: Float, viewportWidth: Float, viewportHeight: Float, zoom: Float) {
        this.displayDensity = density
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        this.currentZoom = zoom
    }

    /**
     * Calculates spacing for a list of pages
     */
    fun calculateSpacing(pages: List<PageInfo>): SpacingResult {
        if (pages.isEmpty()) {
            return createDefaultSpacing()
        }

        return if (configuration.autoSpacing) {
            calculateAdaptiveSpacing(pages)
        } else {
            calculateManualSpacing()
        }
    }

    /**
     * Calculates spacing between two specific pages
     */
    fun calculateInterPageSpacing(currentPage: PageInfo, nextPage: PageInfo?): Float {
        if (!configuration.autoSpacing) {
            return dpToPx(configuration.spacingDp.toFloat())
        }

        if (nextPage == null) {
            return dpToPx(spacingConfig.baseSpacingDp)
        }

        var spacing = dpToPx(spacingConfig.baseSpacingDp)

        // Adjust for aspect ratio differences
        val currentAspectRatio = currentPage.getAspectRatio()
        val nextAspectRatio = nextPage.getAspectRatio()
        val aspectRatioDiff = abs(currentAspectRatio - nextAspectRatio)

        if (aspectRatioDiff > spacingConfig.aspectRatioThreshold) {
            spacing *= 1.5f // More spacing for different aspect ratios
        }

        // Adjust for zoom level
        spacing *= calculateZoomSpacingFactor()

        // Adjust for scroll direction
        spacing *= getDirectionSpacingFactor()

        // Clamp to min/max values
        return clampSpacing(spacing)
    }

    /**
     * Calculates adaptive spacing based on content and layout
     */
    private fun calculateAdaptiveSpacing(pages: List<PageInfo>): SpacingResult {
        val baseSpacing = dpToPx(spacingConfig.baseSpacingDp)
        val zoomFactor = calculateZoomSpacingFactor()
        val directionFactor = getDirectionSpacingFactor()

        // Calculate responsive factors
        val viewportFactor = calculateViewportFactor()
        val contentFactor = calculateContentFactor(pages)

        // Calculate inter-page spacing
        val interPageSpacing = baseSpacing * zoomFactor * directionFactor * viewportFactor * contentFactor

        // Calculate edge spacing
        val edgeSpacing = calculateEdgeSpacing(interPageSpacing)

        return SpacingResult(
            topSpacing = edgeSpacing.first,
            bottomSpacing = edgeSpacing.second,
            leftSpacing = edgeSpacing.third,
            rightSpacing = edgeSpacing.fourth,
            interPageSpacing = clampSpacing(interPageSpacing),
            adaptiveSpacing = true
        )
    }

    /**
     * Calculates manual spacing from configuration
     */
    private fun calculateManualSpacing(): SpacingResult {
        val spacing = dpToPx(configuration.spacingDp.toFloat())
        val edgeSpacing = spacing * 0.5f // Half spacing at edges

        return SpacingResult(
            topSpacing = edgeSpacing,
            bottomSpacing = edgeSpacing,
            leftSpacing = edgeSpacing,
            rightSpacing = edgeSpacing,
            interPageSpacing = spacing,
            adaptiveSpacing = false
        )
    }

    /**
     * Calculates zoom-based spacing factor
     */
    private fun calculateZoomSpacingFactor(): Float {
        return when {
            currentZoom < 0.5f -> 0.5f // Less spacing when very zoomed out
            currentZoom < 1f -> 0.75f // Reduced spacing when zoomed out
            currentZoom > 2f -> spacingConfig.zoomSpacingFactor // More spacing when zoomed in
            currentZoom > 4f -> spacingConfig.zoomSpacingFactor * 1.5f // Even more when very zoomed in
            else -> 1f // Normal spacing at 1x zoom
        }
    }

    /**
     * Gets spacing factor based on scroll direction
     */
    private fun getDirectionSpacingFactor(): Float {
        return when (configuration.pageScrollDirection) {
            ScrollDirection.VERTICAL -> 1f
            ScrollDirection.HORIZONTAL -> 1.2f // Slightly more spacing for horizontal scroll
            ScrollDirection.BOTH -> 0.8f // Less spacing for both directions
        }
    }

    /**
     * Calculates viewport-based spacing factor
     */
    private fun calculateViewportFactor(): Float {
        if (!spacingConfig.responsiveSpacing || viewportWidth <= 0 || viewportHeight <= 0) {
            return 1f
        }

        val viewportAspectRatio = viewportWidth / viewportHeight

        return when {
            viewportAspectRatio > 1.5f -> 1.2f // Landscape - more spacing
            viewportAspectRatio < 0.7f -> 0.8f // Portrait - less spacing
            else -> 1f // Square-ish - normal spacing
        }
    }

    /**
     * Calculates content-aware spacing factor
     */
    private fun calculateContentFactor(pages: List<PageInfo>): Float {
        if (!spacingConfig.contentAwareSpacing || pages.isEmpty()) {
            return 1f
        }

        // Calculate average aspect ratio variance
        val aspectRatios = pages.map { it.getAspectRatio() }
        val averageAspectRatio = aspectRatios.average()
        val variance = aspectRatios.map { (it - averageAspectRatio).pow(2) }.average()

        return when {
            variance > 0.5 -> 1.3f // High variance - more spacing
            variance > 0.2 -> 1.1f // Medium variance - slightly more spacing
            else -> 1f // Low variance - normal spacing
        }
    }

    /**
     * Calculates edge spacing (top, bottom, left, right)
     */
    private fun calculateEdgeSpacing(interPageSpacing: Float): Tuple4<Float, Float, Float, Float> {
        val edgeFactor = 0.6f // Edge spacing is typically less than inter-page spacing
        val baseEdgeSpacing = interPageSpacing * edgeFactor

        return when (configuration.pageScrollDirection) {
            ScrollDirection.VERTICAL -> Tuple4(
                baseEdgeSpacing, // top
                baseEdgeSpacing, // bottom
                baseEdgeSpacing * 0.5f, // left
                baseEdgeSpacing * 0.5f  // right
            )
            ScrollDirection.HORIZONTAL -> Tuple4(
                baseEdgeSpacing * 0.5f, // top
                baseEdgeSpacing * 0.5f, // bottom
                baseEdgeSpacing, // left
                baseEdgeSpacing  // right
            )
            ScrollDirection.BOTH -> Tuple4(
                baseEdgeSpacing, // top
                baseEdgeSpacing, // bottom
                baseEdgeSpacing, // left
                baseEdgeSpacing  // right
            )
        }
    }

    /**
     * Creates default spacing when no pages are available
     */
    private fun createDefaultSpacing(): SpacingResult {
        val defaultSpacing = dpToPx(spacingConfig.baseSpacingDp)
        val edgeSpacing = defaultSpacing * 0.5f

        return SpacingResult(
            topSpacing = edgeSpacing,
            bottomSpacing = edgeSpacing,
            leftSpacing = edgeSpacing,
            rightSpacing = edgeSpacing,
            interPageSpacing = defaultSpacing,
            adaptiveSpacing = configuration.autoSpacing
        )
    }

    /**
     * Converts DP to pixels
     */
    private fun dpToPx(dp: Float): Float {
        return dp * displayDensity
    }

    /**
     * Clamps spacing to configured min/max values
     */
    private fun clampSpacing(spacing: Float): Float {
        val minSpacing = dpToPx(spacingConfig.minSpacingDp)
        val maxSpacing = dpToPx(spacingConfig.maxSpacingDp)
        return spacing.coerceIn(minSpacing, maxSpacing)
    }

    /**
     * Calculates responsive spacing based on screen size
     */
    fun calculateResponsiveSpacing(screenWidthDp: Float, screenHeightDp: Float): Float {
        val screenSize = minOf(screenWidthDp, screenHeightDp)

        return when {
            screenSize < 360 -> spacingConfig.baseSpacingDp * 0.7f // Small screens
            screenSize < 480 -> spacingConfig.baseSpacingDp * 0.8f // Medium screens
            screenSize < 600 -> spacingConfig.baseSpacingDp * 1f   // Normal screens
            screenSize < 840 -> spacingConfig.baseSpacingDp * 1.2f // Large screens
            else -> spacingConfig.baseSpacingDp * 1.4f              // Extra large screens
        }
    }

    /**
     * Gets spacing for shadow/elevation effects
     */
    fun getShadowSpacing(): Float {
        val baseSpacing = dpToPx(2f) // 2dp base shadow
        return baseSpacing * currentZoom.coerceIn(0.5f, 2f)
    }

    /**
     * Gets spacing for page borders
     */
    fun getBorderSpacing(): Float {
        return dpToPx(1f) // 1dp border spacing
    }

    /**
     * Calculates spacing for different layout modes
     */
    fun calculateLayoutModeSpacing(mode: LayoutMode): SpacingResult {
        val baseResult = calculateSpacing(emptyList())
        val factor = when (mode) {
            LayoutMode.SINGLE_PAGE -> 1f
            LayoutMode.CONTINUOUS -> 1f
            LayoutMode.FACING_PAGES -> 0.5f // Less spacing between facing pages
            LayoutMode.MAGAZINE -> 0.3f // Minimal spacing for magazine layout
        }

        return baseResult.copy(
            interPageSpacing = baseResult.interPageSpacing * factor
        )
    }

    enum class LayoutMode {
        SINGLE_PAGE,
        CONTINUOUS,
        FACING_PAGES,
        MAGAZINE
    }

    /**
     * Helper class for 4-tuple values
     */
    private data class Tuple4<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    /**
     * Gets spacing statistics for debugging
     */
    fun getSpacingStatistics(pages: List<PageInfo>): SpacingStatistics {
        val result = calculateSpacing(pages)

        return SpacingStatistics(
            isAdaptive = result.adaptiveSpacing,
            interPageSpacing = result.interPageSpacing,
            totalVerticalSpacing = result.getTotalVerticalSpacing(),
            totalHorizontalSpacing = result.getTotalHorizontalSpacing(),
            zoomFactor = calculateZoomSpacingFactor(),
            directionFactor = getDirectionSpacingFactor(),
            viewportFactor = calculateViewportFactor(),
            contentFactor = if (pages.isNotEmpty()) calculateContentFactor(pages) else 1f
        )
    }

    data class SpacingStatistics(
        val isAdaptive: Boolean,
        val interPageSpacing: Float,
        val totalVerticalSpacing: Float,
        val totalHorizontalSpacing: Float,
        val zoomFactor: Float,
        val directionFactor: Float,
        val viewportFactor: Float,
        val contentFactor: Float
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Spacing Statistics:")
                appendLine("  Mode: ${if (isAdaptive) "Adaptive" else "Manual"}")
                appendLine("  Inter-page: ${interPageSpacing.toInt()}px")
                appendLine("  Total Vertical: ${totalVerticalSpacing.toInt()}px")
                appendLine("  Total Horizontal: ${totalHorizontalSpacing.toInt()}px")
                appendLine("  Factors: zoom=${String.format("%.2f", zoomFactor)}, direction=${String.format("%.2f", directionFactor)}")
                appendLine("           viewport=${String.format("%.2f", viewportFactor)}, content=${String.format("%.2f", contentFactor)}")
            }
        }
    }
}