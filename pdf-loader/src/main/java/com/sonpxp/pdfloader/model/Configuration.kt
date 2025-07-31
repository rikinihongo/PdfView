package com.sonpxp.pdfloader.model

import android.graphics.Color
import androidx.annotation.ColorInt
import com.sonpxp.pdfloader.AnimationType
import com.sonpxp.pdfloader.FitPolicy
import com.sonpxp.pdfloader.MinimapPosition
import com.sonpxp.pdfloader.PageNumberPosition
import com.sonpxp.pdfloader.RenderQuality
import com.sonpxp.pdfloader.ScrollDirection
import com.sonpxp.pdfloader.listeners.drawing.OnDrawAllListener
import com.sonpxp.pdfloader.listeners.drawing.OnDrawListener
import com.sonpxp.pdfloader.listeners.error.OnErrorListener
import com.sonpxp.pdfloader.listeners.interaction.OnDoubleTapListener
import com.sonpxp.pdfloader.listeners.interaction.OnLinkTapListener
import com.sonpxp.pdfloader.listeners.interaction.OnLongPressListener
import com.sonpxp.pdfloader.listeners.interaction.OnTapListener
import com.sonpxp.pdfloader.listeners.interaction.OnZoomListener
import com.sonpxp.pdfloader.listeners.load.OnLoadCompleteListener
import com.sonpxp.pdfloader.listeners.load.OnProgressListener
import com.sonpxp.pdfloader.listeners.load.OnRenderListener
import com.sonpxp.pdfloader.listeners.load.OnRenderProgressListener
import com.sonpxp.pdfloader.listeners.page.OnPageChangeListener
import com.sonpxp.pdfloader.listeners.page.OnPageErrorListener
import com.sonpxp.pdfloader.listeners.page.OnPageScrollListener
import com.sonpxp.pdfloader.source.DocumentSource

/**
 * Configuration class that holds all PDF viewer settings
 * Used internally by the Configurator to build and store configuration
 */
data class Configuration(
    // ===== DATA SOURCE =====
    val documentSource: DocumentSource? = null,

    // ===== PAGE CONFIGURATION =====
    val pages: IntArray? = null, // null means all pages
    val defaultPage: Int = 0,
    val nightMode: Boolean = false,
    val autoSpacing: Boolean = true,
    val spacingDp: Int = 0,
    val pageFitPolicy: FitPolicy = FitPolicy.WIDTH,
    val pageSnap: Boolean = true,
    val fitEachPage: Boolean = false,

    // ===== SWIPE & SCROLL CONFIGURATION =====
    val swipeEnabled: Boolean = true,
    val swipeHorizontal: Boolean = false,
    val pageFling: Boolean = false,
    val pageScrollDirection: ScrollDirection = ScrollDirection.VERTICAL,

    // ===== ZOOM & GESTURES =====
    val doubleTapEnabled: Boolean = true,
    val doubleTapZoomScale: Float = 2.0f,
    val maxZoom: Float = 5.0f,
    val minZoom: Float = 1.0f,
    val zoomCentered: Boolean = false,
    val pinchZoomEnabled: Boolean = true,

    // ===== RENDERING & VISUAL =====
    val antialiasingEnabled: Boolean = true,
    val annotationRenderingEnabled: Boolean = true,
    @ColorInt val invalidPageColor: Int = Color.WHITE,
    @ColorInt val backgroundColor: Int = Color.LTGRAY,
    val bestQuality: Boolean = false,
    val renderDuringScale: Boolean = false,
    val offscreenPageLimit: Int = 1,

    // ===== SECURITY =====
    val password: String? = null,
    val textSelectionEnabled: Boolean = true,
    val copyEnabled: Boolean = true,
    val printEnabled: Boolean = true,

    // ===== UI ELEMENTS =====
    val scrollHandle: Any? = null, // Will be typed properly when ScrollHandle is implemented
    val showMinimap: Boolean = false,
    val minimapPosition: MinimapPosition = MinimapPosition.TOP_RIGHT,
    val showPageNumber: Boolean = false,
    val pageNumberPosition: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val showPageWithAnimation: Boolean = true,
    val animationType: AnimationType = AnimationType.NONE,
    val animationDuration: Long = 400,

    // ===== PERFORMANCE =====
    val cacheSize: Int = 120, // MB
    val renderQuality: RenderQuality = RenderQuality.MEDIUM,
    val prerenderPages: Int = 0,
    val backgroundThreads: Int = 2,
    val lowMemoryMode: Boolean = false,

    // ===== CALLBACKS & LISTENERS =====
    val onLoadCompleteListener: OnLoadCompleteListener? = null,
    val onErrorListener: OnErrorListener? = null,
    val onPageErrorListener: OnPageErrorListener? = null,
    val onRenderListener: OnRenderListener? = null,
    val onPageChangeListener: OnPageChangeListener? = null,
    val onPageScrollListener: OnPageScrollListener? = null,
    val onDrawListener: OnDrawListener? = null,
    val onDrawAllListener: OnDrawAllListener? = null,
    val onTapListener: OnTapListener? = null,
    val onDoubleTapListener: OnDoubleTapListener? = null,
    val onLongPressListener: OnLongPressListener? = null,
    val onZoomListener: OnZoomListener? = null,
    val onLinkTapListener: OnLinkTapListener? = null,
    val onProgressListener: OnProgressListener? = null,
    val onRenderProgressListener: OnRenderProgressListener? = null
) {

    /**
     * Validates the configuration and throws exceptions for invalid settings
     */
    fun validate() {
        require(documentSource != null) { "Document source must be provided" }
        require(defaultPage >= 0) { "Default page must be >= 0" }
        require(spacingDp >= 0) { "Spacing cannot be negative" }
        require(doubleTapZoomScale > 0) { "Double tap zoom scale must be positive" }
        require(maxZoom > minZoom) { "Max zoom must be greater than min zoom" }
        require(minZoom > 0) { "Min zoom must be positive" }
        require(offscreenPageLimit >= 0) { "Offscreen page limit cannot be negative" }
        require(cacheSize > 0) { "Cache size must be positive" }
        require(prerenderPages >= 0) { "Prerender pages cannot be negative" }
        require(backgroundThreads > 0) { "Background threads must be positive" }
        require(animationDuration >= 0) { "Animation duration cannot be negative" }

        // Validate pages array if provided
        pages?.let { pageArray ->
            require(pageArray.isNotEmpty()) { "Pages array cannot be empty if provided" }
            require(pageArray.all { it >= 0 }) { "All page numbers must be >= 0" }
            require(defaultPage < pageArray.size) {
                "Default page index must be within pages array bounds"
            }
        }
    }

    /**
     * Gets the effective page list (either specified pages or all pages)
     */
    fun getEffectivePages(totalPages: Int): IntArray {
        return pages ?: IntArray(totalPages) { it }
    }

    /**
     * Checks if a specific page should be displayed
     */
    fun shouldDisplayPage(pageNumber: Int): Boolean {
        return pages?.contains(pageNumber) ?: true
    }

    /**
     * Gets the display index for a page number
     * Returns -1 if page is not displayed
     */
    fun getDisplayIndex(pageNumber: Int): Int {
        return pages?.indexOf(pageNumber) ?: pageNumber
    }

    /**
     * Gets the page number for a display index
     */
    fun getPageNumber(displayIndex: Int): Int {
        return pages?.getOrNull(displayIndex) ?: displayIndex
    }

    /**
     * Gets the total number of pages to display
     */
    fun getDisplayPageCount(totalPages: Int): Int {
        return pages?.size ?: totalPages
    }

    /**
     * Creates a copy with updated source
     */
    fun withSource(newSource: DocumentSource): Configuration {
        return copy(documentSource = newSource)
    }

    /**
     * Creates a performance-optimized copy for low-end devices
     */
    fun optimizeForLowMemory(): Configuration {
        return copy(
            lowMemoryMode = true,
            renderQuality = RenderQuality.LOW,
            cacheSize = 50,
            prerenderPages = 0,
            backgroundThreads = 1,
            bestQuality = false,
            renderDuringScale = false,
            antialiasingEnabled = false
        )
    }

    /**
     * Creates a quality-optimized copy for high-end devices
     */
    fun optimizeForQuality(): Configuration {
        return copy(
            lowMemoryMode = false,
            renderQuality = RenderQuality.HIGH,
            cacheSize = 200,
            prerenderPages = 2,
            backgroundThreads = 4,
            bestQuality = true,
            renderDuringScale = true,
            antialiasingEnabled = true
        )
    }

    /**
     * Creates a copy with all listeners cleared
     */
    fun clearListeners(): Configuration {
        return copy(
            onLoadCompleteListener = null,
            onErrorListener = null,
            onPageErrorListener = null,
            onRenderListener = null,
            onPageChangeListener = null,
            onPageScrollListener = null,
            onDrawListener = null,
            onDrawAllListener = null,
            onTapListener = null,
            onDoubleTapListener = null,
            onLongPressListener = null,
            onZoomListener = null,
            onLinkTapListener = null,
            onProgressListener = null,
            onRenderProgressListener = null
        )
    }

    /**
     * Custom equals that properly handles arrays and listeners
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Configuration

        if (documentSource != other.documentSource) return false
        if (pages != null) {
            if (other.pages == null) return false
            if (!pages.contentEquals(other.pages)) return false
        } else if (other.pages != null) return false

        return defaultPage == other.defaultPage &&
                nightMode == other.nightMode &&
                autoSpacing == other.autoSpacing &&
                spacingDp == other.spacingDp &&
                pageFitPolicy == other.pageFitPolicy &&
                pageSnap == other.pageSnap &&
                fitEachPage == other.fitEachPage &&
                swipeEnabled == other.swipeEnabled &&
                swipeHorizontal == other.swipeHorizontal &&
                pageFling == other.pageFling &&
                pageScrollDirection == other.pageScrollDirection &&
                doubleTapEnabled == other.doubleTapEnabled &&
                doubleTapZoomScale == other.doubleTapZoomScale &&
                maxZoom == other.maxZoom &&
                minZoom == other.minZoom &&
                zoomCentered == other.zoomCentered &&
                pinchZoomEnabled == other.pinchZoomEnabled &&
                antialiasingEnabled == other.antialiasingEnabled &&
                annotationRenderingEnabled == other.annotationRenderingEnabled &&
                invalidPageColor == other.invalidPageColor &&
                backgroundColor == other.backgroundColor &&
                bestQuality == other.bestQuality &&
                renderDuringScale == other.renderDuringScale &&
                offscreenPageLimit == other.offscreenPageLimit &&
                password == other.password &&
                textSelectionEnabled == other.textSelectionEnabled &&
                copyEnabled == other.copyEnabled &&
                printEnabled == other.printEnabled &&
                scrollHandle == other.scrollHandle &&
                showMinimap == other.showMinimap &&
                minimapPosition == other.minimapPosition &&
                showPageNumber == other.showPageNumber &&
                pageNumberPosition == other.pageNumberPosition &&
                showPageWithAnimation == other.showPageWithAnimation &&
                animationType == other.animationType &&
                animationDuration == other.animationDuration &&
                cacheSize == other.cacheSize &&
                renderQuality == other.renderQuality &&
                prerenderPages == other.prerenderPages &&
                backgroundThreads == other.backgroundThreads &&
                lowMemoryMode == other.lowMemoryMode
        // Note: Listeners are not compared as they are functional interfaces
    }

    /**
     * Custom hashCode that properly handles arrays
     */
    override fun hashCode(): Int {
        var result = documentSource?.hashCode() ?: 0
        result = 31 * result + (pages?.contentHashCode() ?: 0)
        result = 31 * result + defaultPage
        result = 31 * result + nightMode.hashCode()
        result = 31 * result + autoSpacing.hashCode()
        result = 31 * result + spacingDp
        result = 31 * result + pageFitPolicy.hashCode()
        result = 31 * result + pageSnap.hashCode()
        result = 31 * result + fitEachPage.hashCode()
        result = 31 * result + swipeEnabled.hashCode()
        result = 31 * result + swipeHorizontal.hashCode()
        result = 31 * result + pageFling.hashCode()
        result = 31 * result + pageScrollDirection.hashCode()
        result = 31 * result + doubleTapEnabled.hashCode()
        result = 31 * result + doubleTapZoomScale.hashCode()
        result = 31 * result + maxZoom.hashCode()
        result = 31 * result + minZoom.hashCode()
        result = 31 * result + zoomCentered.hashCode()
        result = 31 * result + pinchZoomEnabled.hashCode()
        result = 31 * result + antialiasingEnabled.hashCode()
        result = 31 * result + annotationRenderingEnabled.hashCode()
        result = 31 * result + invalidPageColor
        result = 31 * result + backgroundColor
        result = 31 * result + bestQuality.hashCode()
        result = 31 * result + renderDuringScale.hashCode()
        result = 31 * result + offscreenPageLimit
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + textSelectionEnabled.hashCode()
        result = 31 * result + copyEnabled.hashCode()
        result = 31 * result + printEnabled.hashCode()
        result = 31 * result + (scrollHandle?.hashCode() ?: 0)
        result = 31 * result + showMinimap.hashCode()
        result = 31 * result + minimapPosition.hashCode()
        result = 31 * result + showPageNumber.hashCode()
        result = 31 * result + pageNumberPosition.hashCode()
        result = 31 * result + showPageWithAnimation.hashCode()
        result = 31 * result + animationType.hashCode()
        result = 31 * result + animationDuration.hashCode()
        result = 31 * result + cacheSize
        result = 31 * result + renderQuality.hashCode()
        result = 31 * result + prerenderPages
        result = 31 * result + backgroundThreads
        result = 31 * result + lowMemoryMode.hashCode()
        return result
    }

    /**
     * Gets a summary of the configuration for debugging
     */
    fun getSummary(): String {
        return buildString {
            appendLine("PDF Configuration Summary:")
            appendLine("  Source: ${documentSource?.getDescription() ?: "None"}")
            appendLine("  Pages: ${pages?.contentToString() ?: "All"}")
            appendLine("  Default Page: $defaultPage")
            appendLine("  Fit Policy: $pageFitPolicy")
            appendLine("  Zoom: ${minZoom}x - ${maxZoom}x (double tap: ${doubleTapZoomScale}x)")
            appendLine("  Quality: $renderQuality")
            appendLine("  Cache: ${cacheSize}MB")
            appendLine("  Features: night=$nightMode, swipe=$swipeEnabled, snap=$pageSnap")
            appendLine("  Performance: lowMemory=$lowMemoryMode, threads=$backgroundThreads")
        }
    }
}