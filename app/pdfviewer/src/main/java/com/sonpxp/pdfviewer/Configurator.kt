package com.sonpxp.pdfviewer

import android.content.Context
import android.net.Uri
import androidx.annotation.ColorInt
import com.sonpxp.pdfviewer.layout.FitPolicy
import com.sonpxp.pdfviewer.listeners.drawing.OnDrawAllListener
import com.sonpxp.pdfviewer.listeners.drawing.OnDrawListener
import com.sonpxp.pdfviewer.listeners.error.OnErrorListener
import com.sonpxp.pdfviewer.listeners.interaction.OnDoubleTapListener
import com.sonpxp.pdfviewer.listeners.interaction.OnLinkTapListener
import com.sonpxp.pdfviewer.listeners.interaction.OnLongPressListener
import com.sonpxp.pdfviewer.listeners.interaction.OnTapListener
import com.sonpxp.pdfviewer.listeners.interaction.OnZoomListener
import com.sonpxp.pdfviewer.listeners.load.OnLoadCompleteListener
import com.sonpxp.pdfviewer.listeners.load.OnProgressListener
import com.sonpxp.pdfviewer.listeners.load.OnRenderListener
import com.sonpxp.pdfviewer.listeners.load.OnRenderProgressListener
import com.sonpxp.pdfviewer.listeners.page.OnPageChangeListener
import com.sonpxp.pdfviewer.listeners.page.OnPageErrorListener
import com.sonpxp.pdfviewer.listeners.page.OnPageScrollListener
import com.sonpxp.pdfviewer.model.Configuration
import com.sonpxp.pdfviewer.source.DocumentSource
import com.sonpxp.pdfviewer.ui.minimap.MinimapPosition
import com.sonpxp.pdfviewer.ui.pagenumber.PageNumberPosition
import com.sonpxp.pdfviewer.ui.scrollhandle.ScrollHandle
import com.yourpackage.pdfviewer.listeners.*
import com.yourpackage.pdfviewer.model.*
import java.io.File
import java.io.InputStream

/**
 * Configurator class for building PDFView with fluent API
 */
class Configurator(private val context: Context) {

    // ===== INTERNAL CONFIGURATION STATE =====
    private var documentSource: DocumentSource? = null
    private var config = Configuration()

    // ===== DATA SOURCES =====

    fun fromUri(uri: Uri): Configurator {
        this.documentSource = UriSource(uri)
        return this
    }

    fun fromFile(file: File): Configurator {
        this.documentSource = FileSource(file)
        return this
    }

    fun fromBytes(bytes: ByteArray): Configurator {
        this.documentSource = ByteArraySource(bytes)
        return this
    }

    fun fromStream(stream: InputStream): Configurator {
        this.documentSource = StreamSource(stream)
        return this
    }

    fun fromAsset(assetName: String): Configurator {
        this.documentSource = AssetSource(context, assetName)
        return this
    }

    fun fromSource(source: DocumentSource): Configurator {
        this.documentSource = source
        return this
    }

    // ===== PAGE CONFIGURATION =====

    fun pages(vararg pageNumbers: Int): Configurator {
        config = config.copy(pages = pageNumbers.toList())
        return this
    }

    fun defaultPage(page: Int): Configurator {
        config = config.copy(defaultPage = page)
        return this
    }

    fun nightMode(enabled: Boolean): Configurator {
        config = config.copy(nightMode = enabled)
        return this
    }

    fun autoSpacing(enabled: Boolean): Configurator {
        config = config.copy(autoSpacing = enabled)
        return this
    }

    fun spacing(spacingDp: Int): Configurator {
        config = config.copy(spacing = spacingDp)
        return this
    }

    fun pageFitPolicy(policy: FitPolicy): Configurator {
        config = config.copy(pageFitPolicy = policy)
        return this
    }

    fun pageSnap(enabled: Boolean): Configurator {
        config = config.copy(pageSnap = enabled)
        return this
    }

    fun fitEachPage(enabled: Boolean): Configurator {
        config = config.copy(fitEachPage = enabled)
        return this
    }

    // ===== SWIPE & SCROLL CONFIGURATION =====

    fun enableSwipe(enabled: Boolean): Configurator {
        config = config.copy(swipeEnabled = enabled)
        return this
    }

    fun swipeHorizontal(horizontal: Boolean): Configurator {
        config = config.copy(swipeHorizontal = horizontal)
        return this
    }

    fun pageFling(enabled: Boolean): Configurator {
        config = config.copy(pageFling = enabled)
        return this
    }

    fun pageScrollDirection(direction: ScrollDirection): Configurator {
        config = config.copy(pageScrollDirection = direction)
        return this
    }

    // ===== ZOOM & GESTURES =====

    fun enableDoubletap(enabled: Boolean): Configurator {
        config = config.copy(doubleTapEnabled = enabled)
        return this
    }

    fun doubletapZoomScale(scale: Float): Configurator {
        config = config.copy(doubleTapZoomScale = scale)
        return this
    }

    fun maxZoom(maxZoom: Float): Configurator {
        config = config.copy(maxZoom = maxZoom)
        return this
    }

    fun minZoom(minZoom: Float): Configurator {
        config = config.copy(minZoom = minZoom)
        return this
    }

    fun zoomCentered(centered: Boolean): Configurator {
        config = config.copy(zoomCentered = centered)
        return this
    }

    fun pinchZoom(enabled: Boolean): Configurator {
        config = config.copy(pinchZoomEnabled = enabled)
        return this
    }

    // ===== RENDERING & VISUAL =====

    fun enableAntialiasing(enabled: Boolean): Configurator {
        config = config.copy(antialiasingEnabled = enabled)
        return this
    }

    fun enableAnnotationRendering(enabled: Boolean): Configurator {
        config = config.copy(annotationRenderingEnabled = enabled)
        return this
    }

    fun invalidPageColor(@ColorInt color: Int): Configurator {
        config = config.copy(invalidPageColor = color)
        return this
    }

    fun backgroundColor(@ColorInt color: Int): Configurator {
        config = config.copy(backgroundColor = color)
        return this
    }

    fun bestQuality(enabled: Boolean): Configurator {
        config = config.copy(bestQuality = enabled)
        return this
    }

    fun renderDuringScale(enabled: Boolean): Configurator {
        config = config.copy(renderDuringScale = enabled)
        return this
    }

    fun offscreenPageLimit(limit: Int): Configurator {
        config = config.copy(offscreenPageLimit = limit)
        return this
    }

    // ===== SECURITY =====

    fun password(password: String?): Configurator {
        config = config.copy(password = password)
        return this
    }

    fun enableTextSelection(enabled: Boolean): Configurator {
        config = config.copy(textSelectionEnabled = enabled)
        return this
    }

    fun enableCopy(enabled: Boolean): Configurator {
        config = config.copy(copyEnabled = enabled)
        return this
    }

    fun enablePrint(enabled: Boolean): Configurator {
        config = config.copy(printEnabled = enabled)
        return this
    }

    // ===== UI ELEMENTS =====

    fun scrollHandle(handle: ScrollHandle?): Configurator {
        config = config.copy(scrollHandle = handle)
        return this
    }

    fun showMinimap(show: Boolean): Configurator {
        config = config.copy(showMinimap = show)
        return this
    }

    fun minimapPosition(position: MinimapPosition): Configurator {
        config = config.copy(minimapPosition = position)
        return this
    }

    fun showPageNumber(show: Boolean): Configurator {
        config = config.copy(showPageNumber = show)
        return this
    }

    fun pageNumberPosition(position: PageNumberPosition): Configurator {
        config = config.copy(pageNumberPosition = position)
        return this
    }

    fun showPageWithAnimation(animate: Boolean): Configurator {
        config = config.copy(showPageWithAnimation = animate)
        return this
    }

    fun animationType(type: AnimationType): Configurator {
        config = config.copy(animationType = type)
        return this
    }

    fun animationDuration(durationMs: Long): Configurator {
        config = config.copy(animationDuration = durationMs)
        return this
    }

    // ===== PERFORMANCE =====

    fun cacheSize(sizeInMB: Int): Configurator {
        config = config.copy(cacheSize = sizeInMB)
        return this
    }

    fun renderQuality(quality: RenderQuality): Configurator {
        config = config.copy(renderQuality = quality)
        return this
    }

    fun prerenderPages(count: Int): Configurator {
        config = config.copy(prerenderPages = count)
        return this
    }

    fun backgroundThreads(count: Int): Configurator {
        config = config.copy(backgroundThreads = count)
        return this
    }

    fun lowMemoryMode(enabled: Boolean): Configurator {
        config = config.copy(lowMemoryMode = enabled)
        return this
    }

    // ===== CALLBACKS & LISTENERS =====

    fun onLoad(listener: OnLoadCompleteListener): Configurator {
        config = config.copy(onLoadCompleteListener = listener)
        return this
    }

    fun onError(listener: OnErrorListener): Configurator {
        config = config.copy(onErrorListener = listener)
        return this
    }

    fun onPageError(listener: OnPageErrorListener): Configurator {
        config = config.copy(onPageErrorListener = listener)
        return this
    }

    fun onRender(listener: OnRenderListener): Configurator {
        config = config.copy(onRenderListener = listener)
        return this
    }

    fun onPageChange(listener: OnPageChangeListener): Configurator {
        config = config.copy(onPageChangeListener = listener)
        return this
    }

    fun onPageScroll(listener: OnPageScrollListener): Configurator {
        config = config.copy(onPageScrollListener = listener)
        return this
    }

    fun onDraw(listener: OnDrawListener): Configurator {
        config = config.copy(onDrawListener = listener)
        return this
    }

    fun onDrawAll(listener: OnDrawAllListener): Configurator {
        config = config.copy(onDrawAllListener = listener)
        return this
    }

    fun onTap(listener: OnTapListener): Configurator {
        config = config.copy(onTapListener = listener)
        return this
    }

    fun onDoubleTap(listener: OnDoubleTapListener): Configurator {
        config = config.copy(onDoubleTapListener = listener)
        return this
    }

    fun onLongPress(listener: OnLongPressListener): Configurator {
        config = config.copy(onLongPressListener = listener)
        return this
    }

    fun onZoom(listener: OnZoomListener): Configurator {
        config = config.copy(onZoomListener = listener)
        return this
    }

    fun onLinkTap(listener: OnLinkTapListener): Configurator {
        config = config.copy(onLinkTapListener = listener)
        return this
    }

    fun onProgress(listener: OnProgressListener): Configurator {
        config = config.copy(onProgressListener = listener)
        return this
    }

    fun onRenderProgress(listener: OnRenderProgressListener): Configurator {
        config = config.copy(onRenderProgressListener = listener)
        return this
    }

    // ===== FINAL CONFIGURATION =====

    /**
     * Apply configuration to existing PDFView
     */
    fun into(pdfView: PDFView): PDFView {
        requireNotNull(documentSource) { "Document source must be set" }
        pdfView.configure(documentSource!!, config)
        return pdfView
    }

    /**
     * Create new PDFView and apply configuration
     */
    fun load(): PDFView {
        requireNotNull(documentSource) { "Document source must be set" }
        val pdfView = PDFView(context)
        pdfView.configure(documentSource!!, config)
        return pdfView
    }
}

