package com.sonpxp.pdfloader.core

import com.sonpxp.pdfloader.model.PageInfo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import com.sonpxp.pdfloader.PageRenderState
import com.sonpxp.pdfloader.RenderQuality
import com.sonpxp.pdfloader.exception.PageRenderException
import kotlin.math.roundToInt

/**
 * Handles the rendering of individual PDF pages to bitmaps
 * Provides various rendering options and quality settings
 */
class PageRenderer(
    private val bitmapPool: BitmapPool,
    private val memoryManager: MemoryManager
) {

    companion object {
        private const val TAG = "PageRenderer"
        private const val DEFAULT_BACKGROUND_COLOR = Color.WHITE
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }

    /**
     * Renders a PDF page to a bitmap
     * @param pdfPage the PDF page to render
     * @param pageNumber the page number (0-indexed)
     * @param targetWidth desired width in pixels
     * @param targetHeight desired height in pixels
     * @param quality render quality settings
     * @param backgroundColor background color for the page
     * @param nightMode whether to apply night mode filter
     * @param antialiasing whether to enable antialiasing
     * @param renderAnnotations whether to render annotations
     * @return rendered page information with bitmap
     * @throws PageRenderException if rendering fails
     */
    @Throws(PageRenderException::class)
    fun renderPage(
        pdfPage: PdfRenderer.Page,
        pageNumber: Int,
        targetWidth: Int,
        targetHeight: Int,
        quality: RenderQuality = RenderQuality.MEDIUM,
        backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
        nightMode: Boolean = false,
        antialiasing: Boolean = true,
        renderAnnotations: Boolean = true
    ): RenderedPageResult {

        val startTime = System.currentTimeMillis()

        try {
            // Calculate actual render dimensions based on quality
            val scaleFactor = quality.getScaleFactor()
            val renderWidth = (targetWidth * scaleFactor).roundToInt()
            val renderHeight = (targetHeight * scaleFactor).roundToInt()

            // Check memory requirements
            val requiredMemory = calculateBitmapMemory(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            memoryManager.allocateMemorySafely(requiredMemory, "page $pageNumber rendering")

            // Get bitmap from pool or create new one
            val bitmap = bitmapPool.getBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)

            // Clear bitmap with background color
            bitmap.eraseColor(backgroundColor)

            // Create render destination rect
            val destRect = Rect(0, 0, renderWidth, renderHeight)

            // Configure render mode
            val renderMode = if (renderAnnotations) {
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            } else {
                PdfRenderer.Page.RENDER_MODE_FOR_PRINT
            }

            // Render the page
            pdfPage.render(bitmap, destRect, null, renderMode)

            // Apply post-processing effects
            if (nightMode) {
                applyNightMode(bitmap)
            }

            if (antialiasing && scaleFactor != 1.0f) {
                // Scale down with antialiasing if we rendered at higher resolution
                val finalBitmap = if (scaleFactor > 1.0f) {
                    createScaledBitmap(bitmap, targetWidth, targetHeight)
                } else {
                    bitmap
                }

                // Return original bitmap to pool if we created a scaled version
                if (finalBitmap !== bitmap) {
                    bitmapPool.returnBitmap(bitmap)
                }

                val renderTime = System.currentTimeMillis() - startTime

                return RenderedPageResult(
                    bitmap = finalBitmap,
                    pageInfo = createPageInfo(
                        pageNumber = pageNumber,
                        originalWidth = pdfPage.width.toFloat(),
                        originalHeight = pdfPage.height.toFloat(),
                        renderedWidth = targetWidth.toFloat(),
                        renderedHeight = targetHeight.toFloat(),
                        quality = quality
                    ),
                    renderTimeMs = renderTime,
                    actualRenderSize = Pair(renderWidth, renderHeight),
                    scaleFactor = scaleFactor
                )
            } else {
                val renderTime = System.currentTimeMillis() - startTime

                return RenderedPageResult(
                    bitmap = bitmap,
                    pageInfo = createPageInfo(
                        pageNumber = pageNumber,
                        originalWidth = pdfPage.width.toFloat(),
                        originalHeight = pdfPage.height.toFloat(),
                        renderedWidth = renderWidth.toFloat(),
                        renderedHeight = renderHeight.toFloat(),
                        quality = quality
                    ),
                    renderTimeMs = renderTime,
                    actualRenderSize = Pair(renderWidth, renderHeight),
                    scaleFactor = scaleFactor
                )
            }

        } catch (e: Exception) {
            throw PageRenderException(
                pageNumber = pageNumber,
                message = "Failed to render page: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Renders a portion of a PDF page (for zoomed views)
     * @param pdfPage the PDF page to render
     * @param pageNumber the page number (0-indexed)
     * @param sourceRect the portion of the page to render (in page coordinates)
     * @param targetWidth desired output width in pixels
     * @param targetHeight desired output height in pixels
     * @param quality render quality settings
     * @param backgroundColor background color for the page
     * @param nightMode whether to apply night mode filter
     * @param antialiasing whether to enable antialiasing
     * @return rendered page portion result
     * @throws PageRenderException if rendering fails
     */
    @Throws(PageRenderException::class)
    fun renderPagePortion(
        pdfPage: PdfRenderer.Page,
        pageNumber: Int,
        sourceRect: RectF,
        targetWidth: Int,
        targetHeight: Int,
        quality: RenderQuality = RenderQuality.MEDIUM,
        backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
        nightMode: Boolean = false,
        antialiasing: Boolean = true
    ): RenderedPageResult {

        val startTime = System.currentTimeMillis()

        try {
            // Calculate render dimensions
            val scaleFactor = quality.getScaleFactor()
            val renderWidth = (targetWidth * scaleFactor).roundToInt()
            val renderHeight = (targetHeight * scaleFactor).roundToInt()

            // Check memory requirements
            val requiredMemory = calculateBitmapMemory(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            memoryManager.allocateMemorySafely(requiredMemory, "page $pageNumber portion rendering")

            // Get bitmap from pool
            val bitmap = bitmapPool.getBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(backgroundColor)

            // Convert source rect to page coordinates
            val pageWidth = pdfPage.width
            val pageHeight = pdfPage.height

            val sourceRectInt = Rect(
                (sourceRect.left * pageWidth).roundToInt(),
                (sourceRect.top * pageHeight).roundToInt(),
                (sourceRect.right * pageWidth).roundToInt(),
                (sourceRect.bottom * pageHeight).roundToInt()
            )

            val destRect = Rect(0, 0, renderWidth, renderHeight)

            // Render the page portion
            pdfPage.render(bitmap, destRect, sourceRectInt, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Apply post-processing
            if (nightMode) {
                applyNightMode(bitmap)
            }

            val finalBitmap = if (antialiasing && scaleFactor > 1.0f) {
                val scaled = createScaledBitmap(bitmap, targetWidth, targetHeight)
                bitmapPool.returnBitmap(bitmap)
                scaled
            } else {
                bitmap
            }

            val renderTime = System.currentTimeMillis() - startTime

            return RenderedPageResult(
                bitmap = finalBitmap,
                pageInfo = createPageInfo(
                    pageNumber = pageNumber,
                    originalWidth = pdfPage.width.toFloat(),
                    originalHeight = pdfPage.height.toFloat(),
                    renderedWidth = targetWidth.toFloat(),
                    renderedHeight = targetHeight.toFloat(),
                    quality = quality
                ),
                renderTimeMs = renderTime,
                actualRenderSize = Pair(renderWidth, renderHeight),
                scaleFactor = scaleFactor
            )

        } catch (e: Exception) {
            throw PageRenderException(
                pageNumber = pageNumber,
                message = "Failed to render page portion: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Creates a thumbnail version of a page
     * @param pdfPage the PDF page to render
     * @param pageNumber the page number (0-indexed)
     * @param maxSize maximum size for width or height
     * @param backgroundColor background color
     * @return rendered thumbnail result
     */
    fun renderThumbnail(
        pdfPage: PdfRenderer.Page,
        pageNumber: Int,
        maxSize: Int = 200,
        backgroundColor: Int = DEFAULT_BACKGROUND_COLOR
    ): RenderedPageResult {

        // Calculate thumbnail dimensions maintaining aspect ratio
        val pageWidth = pdfPage.width
        val pageHeight = pdfPage.height
        val aspectRatio = pageWidth.toFloat() / pageHeight.toFloat()

        val (thumbWidth, thumbHeight) = if (aspectRatio > 1.0f) {
            // Landscape: fit to width
            Pair(maxSize, (maxSize / aspectRatio).roundToInt())
        } else {
            // Portrait: fit to height
            Pair((maxSize * aspectRatio).roundToInt(), maxSize)
        }

        return renderPage(
            pdfPage = pdfPage,
            pageNumber = pageNumber,
            targetWidth = thumbWidth,
            targetHeight = thumbHeight,
            quality = RenderQuality.LOW, // Use low quality for thumbnails
            backgroundColor = backgroundColor,
            nightMode = false,
            antialiasing = true,
            renderAnnotations = false // Skip annotations for thumbnails
        )
    }

    private fun applyNightMode(bitmap: Bitmap) {
        // Apply night mode by inverting colors
        val canvas = Canvas(bitmap)
        val nightPaint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, nightPaint)
    }

    private fun createScaledBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val scaledBitmap = bitmapPool.getBitmap(targetWidth, targetHeight, source.config!!)
        val canvas = Canvas(scaledBitmap)

        val srcRect = Rect(0, 0, source.width, source.height)
        val destRect = Rect(0, 0, targetWidth, targetHeight)

        canvas.drawBitmap(source, srcRect, destRect, paint)
        return scaledBitmap
    }

    private fun createPageInfo(
        pageNumber: Int,
        originalWidth: Float,
        originalHeight: Float,
        renderedWidth: Float,
        renderedHeight: Float,
        quality: RenderQuality
    ): PageInfo {
        return PageInfo(
            pageNumber = pageNumber,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            renderedWidth = renderedWidth,
            renderedHeight = renderedHeight,
            zoomLevel = renderedWidth / originalWidth,
            renderState = PageRenderState.RENDERED,
            lastRenderTime = System.currentTimeMillis(),
            renderQuality = quality
        )
    }

    private fun calculateBitmapMemory(width: Int, height: Int, config: Bitmap.Config): Long {
        val bytesPerPixel = when (config) {
            Bitmap.Config.ALPHA_8 -> 1
            Bitmap.Config.RGB_565 -> 2
            Bitmap.Config.ARGB_4444 -> 2
            Bitmap.Config.ARGB_8888 -> 4
            Bitmap.Config.RGBA_F16 -> 8
            else -> 4
        }
        return (width * height * bytesPerPixel).toLong()
    }

    /**
     * Result of a page rendering operation
     */
    data class RenderedPageResult(
        val bitmap: Bitmap,
        val pageInfo: PageInfo,
        val renderTimeMs: Long,
        val actualRenderSize: Pair<Int, Int>,
        val scaleFactor: Float
    ) {
        /**
         * Gets the efficiency of the rendering (pixels per millisecond)
         */
        fun getRenderEfficiency(): Float {
            val totalPixels = actualRenderSize.first * actualRenderSize.second
            return if (renderTimeMs > 0) totalPixels.toFloat() / renderTimeMs.toFloat() else 0f
        }

        /**
         * Gets a summary of the rendering operation
         */
        fun getSummary(): String {
            return "Page ${pageInfo.pageNumber}: ${actualRenderSize.first}x${actualRenderSize.second} " +
                    "in ${renderTimeMs}ms (${String.format("%.1f", getRenderEfficiency())} px/ms)"
        }
    }
}