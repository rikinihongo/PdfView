package com.sonpxp.pdfloader.rendering


import android.graphics.*
import com.sonpxp.pdfloader.model.Configuration

/**
 * Handles antialiasing and smooth rendering for PDF content
 * Provides different antialiasing strategies for various content types
 */
class AntialiasRenderer(
    private val configuration: Configuration
) {

    enum class AntialiasMode {
        DISABLED,       // No antialiasing
        STANDARD,       // Standard Android antialiasing
        ENHANCED,       // Enhanced antialiasing with custom filters
        ADAPTIVE        // Adaptive based on content and zoom
    }

    data class AntialiasConfig(
        val mode: AntialiasMode,
        val textAntialiasing: Boolean,
        val imageAntialiasing: Boolean,
        val pathAntialiasing: Boolean,
        val subpixelText: Boolean,
        val customFilter: Boolean
    )

    private var currentMode = AntialiasMode.STANDARD
    private var zoomThreshold = 1.5f // Switch modes based on zoom

    /**
     * Creates antialiased Paint for text rendering
     */
    fun createTextPaint(zoomLevel: Float = 1f): Paint {
        val config = getAntialiasConfig(zoomLevel)

        return Paint().apply {
            isAntiAlias = config.textAntialiasing
            isSubpixelText = config.subpixelText && zoomLevel >= 1f
            isDither = true
            isFilterBitmap = true
            hinting = Paint.HINTING_ON

            // Enhanced text rendering for high zoom
            if (config.mode == AntialiasMode.ENHANCED && zoomLevel > 2f) {
                flags = flags or Paint.SUBPIXEL_TEXT_FLAG
                flags = flags or Paint.LINEAR_TEXT_FLAG
            }
        }
    }

    /**
     * Creates antialiased Paint for path/vector rendering
     */
    fun createPathPaint(zoomLevel: Float = 1f): Paint {
        val config = getAntialiasConfig(zoomLevel)

        return Paint().apply {
            isAntiAlias = config.pathAntialiasing
            isDither = true
            style = Paint.Style.FILL

            // Use higher quality path rendering for enhanced mode
            if (config.mode == AntialiasMode.ENHANCED) {
                pathEffect = null // Ensure no path effects interfere
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
        }
    }

    /**
     * Creates antialiased Paint for image rendering
     */
    fun createImagePaint(zoomLevel: Float = 1f): Paint {
        val config = getAntialiasConfig(zoomLevel)

        return Paint().apply {
            isAntiAlias = config.imageAntialiasing
            isFilterBitmap = config.imageAntialiasing
            isDither = zoomLevel < 1f // Dither when scaling down

            // Custom filtering for enhanced mode
            if (config.customFilter && config.mode == AntialiasMode.ENHANCED) {
                colorFilter = createEnhancedColorFilter()
            }
        }
    }

    /**
     * Applies antialiasing to a Canvas
     */
    fun applyCanvasAntialiasing(canvas: Canvas, zoomLevel: Float = 1f) {
        val config = getAntialiasConfig(zoomLevel)

        if (config.mode == AntialiasMode.ENHANCED) {
            // Enable high quality rendering
            canvas.drawFilter = PaintFlagsDrawFilter(
                Paint.DITHER_FLAG,
                Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
            )
        } else if (config.mode == AntialiasMode.STANDARD) {
            canvas.drawFilter = PaintFlagsDrawFilter(
                0,
                Paint.ANTI_ALIAS_FLAG
            )
        }
    }

    /**
     * Removes antialiasing from a Canvas
     */
    fun removeCanvasAntialiasing(canvas: Canvas) {
        canvas.drawFilter = null
    }

    /**
     * Creates an antialiased bitmap from source
     */
    fun createAntialiasedBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        zoomLevel: Float = 1f
    ): Bitmap? {
        if (source.isRecycled) return null

        val config = getAntialiasConfig(zoomLevel)
        if (config.mode == AntialiasMode.DISABLED) {
            return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, false)
        }

        try {
            val result = Bitmap.createBitmap(targetWidth, targetHeight, source.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)

            // Apply antialiasing
            applyCanvasAntialiasing(canvas, zoomLevel)

            // Create paint with appropriate settings
            val paint = createImagePaint(zoomLevel)

            // Draw with scaling
            val srcRect = Rect(0, 0, source.width, source.height)
            val dstRect = Rect(0, 0, targetWidth, targetHeight)

            canvas.drawBitmap(source, srcRect, dstRect, paint)

            return result
        } catch (e: OutOfMemoryError) {
            // Fallback to simple scaling
            return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        }
    }

    /**
     * Applies smooth edge detection and enhancement
     */
    fun enhanceEdges(bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled || currentMode != AntialiasMode.ENHANCED) {
            return bitmap
        }

        try {
            val width = bitmap.width
            val height = bitmap.height
            val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

            // Simple edge enhancement using convolution
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val enhancedPixels = applyEdgeEnhancement(pixels, width, height)
            result.setPixels(enhancedPixels, 0, width, 0, 0, width, height)

            return result
        } catch (e: Exception) {
            return bitmap
        }
    }

    /**
     * Gets antialiasing configuration for current zoom level
     */
    private fun getAntialiasConfig(zoomLevel: Float): AntialiasConfig {
        if (!configuration.antialiasingEnabled) {
            return AntialiasConfig(
                mode = AntialiasMode.DISABLED,
                textAntialiasing = false,
                imageAntialiasing = false,
                pathAntialiasing = false,
                subpixelText = false,
                customFilter = false
            )
        }

        return when (currentMode) {
            AntialiasMode.DISABLED -> AntialiasConfig(
                mode = AntialiasMode.DISABLED,
                textAntialiasing = false,
                imageAntialiasing = false,
                pathAntialiasing = false,
                subpixelText = false,
                customFilter = false
            )

            AntialiasMode.STANDARD -> AntialiasConfig(
                mode = AntialiasMode.STANDARD,
                textAntialiasing = true,
                imageAntialiasing = true,
                pathAntialiasing = true,
                subpixelText = zoomLevel >= 1f,
                customFilter = false
            )

            AntialiasMode.ENHANCED -> AntialiasConfig(
                mode = AntialiasMode.ENHANCED,
                textAntialiasing = true,
                imageAntialiasing = true,
                pathAntialiasing = true,
                subpixelText = true,
                customFilter = zoomLevel > 1.5f
            )

            AntialiasMode.ADAPTIVE -> {
                val useEnhanced = zoomLevel > zoomThreshold
                if (useEnhanced) {
                    AntialiasConfig(
                        mode = AntialiasMode.ENHANCED,
                        textAntialiasing = true,
                        imageAntialiasing = true,
                        pathAntialiasing = true,
                        subpixelText = true,
                        customFilter = true
                    )
                } else {
                    AntialiasConfig(
                        mode = AntialiasMode.STANDARD,
                        textAntialiasing = true,
                        imageAntialiasing = zoomLevel >= 0.5f,
                        pathAntialiasing = true,
                        subpixelText = zoomLevel >= 1f,
                        customFilter = false
                    )
                }
            }
        }
    }

    /**
     * Creates enhanced color filter for image quality
     */
    private fun createEnhancedColorFilter(): ColorFilter {
        // Slight sharpening and contrast enhancement
        val matrix = ColorMatrix(floatArrayOf(
            1.1f, 0f, 0f, 0f, -10f,    // Red channel - slight increase
            0f, 1.1f, 0f, 0f, -10f,    // Green channel - slight increase
            0f, 0f, 1.1f, 0f, -10f,    // Blue channel - slight increase
            0f, 0f, 0f, 1f, 0f         // Alpha channel - unchanged
        ))

        return ColorMatrixColorFilter(matrix)
    }

    /**
     * Applies edge enhancement to pixel array
     */
    private fun applyEdgeEnhancement(pixels: IntArray, width: Int, height: Int): IntArray {
        val result = IntArray(pixels.size)

        // Simple sharpening kernel
        val kernel = arrayOf(
            arrayOf(0f, -0.25f, 0f),
            arrayOf(-0.25f, 2f, -0.25f),
            arrayOf(0f, -0.25f, 0f)
        )

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x

                var r = 0f
                var g = 0f
                var b = 0f

                // Apply convolution
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixelIndex = (y + ky) * width + (x + kx)
                        val pixel = pixels[pixelIndex]
                        val weight = kernel[ky + 1][kx + 1]

                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }

                // Clamp values and preserve alpha
                val red = r.toInt().coerceIn(0, 255)
                val green = g.toInt().coerceIn(0, 255)
                val blue = b.toInt().coerceIn(0, 255)
                val alpha = Color.alpha(pixels[index])

                result[index] = Color.argb(alpha, red, green, blue)
            }
        }

        // Copy edges without enhancement
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    result[y * width + x] = pixels[y * width + x]
                }
            }
        }

        return result
    }

    /**
     * Sets antialiasing mode
     */
    fun setAntialiasMode(mode: AntialiasMode) {
        currentMode = mode
    }

    /**
     * Gets current antialiasing mode
     */
    fun getAntialiasMode(): AntialiasMode = currentMode

    /**
     * Sets zoom threshold for adaptive mode
     */
    fun setZoomThreshold(threshold: Float) {
        zoomThreshold = threshold
    }

    /**
     * Checks if antialiasing is enabled
     */
    fun isAntialiasEnabled(): Boolean {
        return configuration.antialiasingEnabled && currentMode != AntialiasMode.DISABLED
    }

    /**
     * Gets antialiasing statistics
     */
    fun getStatistics(): AntialiasStatistics {
        return AntialiasStatistics(
            currentMode = currentMode,
            isEnabled = isAntialiasEnabled(),
            zoomThreshold = zoomThreshold,
            configurationEnabled = configuration.antialiasingEnabled
        )
    }

    data class AntialiasStatistics(
        val currentMode: AntialiasMode,
        val isEnabled: Boolean,
        val zoomThreshold: Float,
        val configurationEnabled: Boolean
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Antialias Renderer Statistics:")
                appendLine("  Current Mode: $currentMode")
                appendLine("  Enabled: $isEnabled")
                appendLine("  Zoom Threshold: $zoomThreshold")
                appendLine("  Config Enabled: $configurationEnabled")
            }
        }
    }
}