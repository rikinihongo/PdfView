package com.sonpxp.pdfloader.rendering


import android.graphics.Bitmap
import android.graphics.Paint
import com.sonpxp.pdfloader.RenderQuality
import com.sonpxp.pdfloader.model.Configuration
import kotlin.math.*

/**
 * Manages rendering quality and performance optimization
 * Controls DPI, bitmap configuration, and quality vs performance trade-offs
 */
class QualityManager(
    private var configuration: Configuration
) {

    // Quality settings
    private var currentQuality = RenderQuality.MEDIUM
    private var adaptiveQualityEnabled = true
    private var performanceMode = false

    // Memory and performance tracking
    private var memoryPressure = 0f // 0.0 to 1.0
    private var renderingPerformance = 1f // 1.0 = good, 0.0 = poor
    private var lastRenderTime = 0L

    data class QualitySettings(
        val renderQuality: RenderQuality,
        val bitmapConfig: Bitmap.Config,
        val scaleFactor: Float,
        val enableAntialiasing: Boolean,
        val enableSubpixelText: Boolean,
        val enableFiltering: Boolean,
        val compressionQuality: Int, // 0-100
        val useHighQualityScaling: Boolean
    )

    data class RenderContext(
        val pageWidth: Float,
        val pageHeight: Float,
        val zoomLevel: Float,
        val isVisible: Boolean,
        val priority: RenderPriority
    )

    enum class RenderPriority {
        IMMEDIATE,  // Currently visible page
        HIGH,       // Adjacent pages
        NORMAL,     // Nearby pages
        LOW,        // Distant pages
        BACKGROUND  // Preload pages
    }

    /**
     * Gets optimal quality settings for given context
     */
    fun getQualitySettings(context: RenderContext): QualitySettings {
        val effectiveQuality = determineEffectiveQuality(context)
        val baseSettings = getBaseQualitySettings(effectiveQuality)

        return applyContextOptimizations(baseSettings, context)
    }

    /**
     * Gets base quality settings for a render quality level
     */
    private fun getBaseQualitySettings(quality: RenderQuality): QualitySettings {
        return when (quality) {
            RenderQuality.LOW -> QualitySettings(
                renderQuality = quality,
                bitmapConfig = Bitmap.Config.RGB_565,
                scaleFactor = 0.5f,
                enableAntialiasing = false,
                enableSubpixelText = false,
                enableFiltering = false,
                compressionQuality = 60,
                useHighQualityScaling = false
            )

            RenderQuality.MEDIUM -> QualitySettings(
                renderQuality = quality,
                bitmapConfig = if (performanceMode) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888,
                scaleFactor = if (performanceMode) 0.75f else 1f,
                enableAntialiasing = !performanceMode,
                enableSubpixelText = !performanceMode,
                enableFiltering = true,
                compressionQuality = 75,
                useHighQualityScaling = !performanceMode
            )

            RenderQuality.HIGH -> QualitySettings(
                renderQuality = quality,
                bitmapConfig = Bitmap.Config.ARGB_8888,
                scaleFactor = 1f,
                enableAntialiasing = true,
                enableSubpixelText = true,
                enableFiltering = true,
                compressionQuality = 90,
                useHighQualityScaling = true
            )

            RenderQuality.ULTRA -> QualitySettings(
                renderQuality = quality,
                bitmapConfig = Bitmap.Config.ARGB_8888,
                scaleFactor = 1.5f,
                enableAntialiasing = true,
                enableSubpixelText = true,
                enableFiltering = true,
                compressionQuality = 95,
                useHighQualityScaling = true
            )
        }
    }

    /**
     * Determines effective quality based on context and performance
     */
    private fun determineEffectiveQuality(context: RenderContext): RenderQuality {
        var targetQuality = configuration.renderQuality

        // Apply adaptive quality adjustments
        if (adaptiveQualityEnabled) {
            targetQuality = applyAdaptiveQuality(targetQuality, context)
        }

        // Apply performance constraints
        if (performanceMode || memoryPressure > 0.7f) {
            targetQuality = downgradeQuality(targetQuality)
        }

        // Apply priority-based adjustments
        targetQuality = applyPriorityAdjustments(targetQuality, context.priority)

        return targetQuality
    }

    /**
     * Applies adaptive quality based on zoom and visibility
     */
    private fun applyAdaptiveQuality(baseQuality: RenderQuality, context: RenderContext): RenderQuality {
        // Reduce quality for very high zoom levels
        if (context.zoomLevel > 4f) {
            return when (baseQuality) {
                RenderQuality.ULTRA -> RenderQuality.HIGH
                RenderQuality.HIGH -> RenderQuality.MEDIUM
                else -> baseQuality
            }
        }

        // Reduce quality for very low zoom levels
        if (context.zoomLevel < 0.5f) {
            return when (baseQuality) {
                RenderQuality.ULTRA -> RenderQuality.HIGH
                RenderQuality.HIGH -> RenderQuality.MEDIUM
                RenderQuality.MEDIUM -> RenderQuality.LOW
                else -> baseQuality
            }
        }

        // Increase quality for normal viewing range
        if (context.isVisible && context.zoomLevel >= 1f && context.zoomLevel <= 2f) {
            return when (baseQuality) {
                RenderQuality.LOW -> RenderQuality.MEDIUM
                RenderQuality.MEDIUM -> if (renderingPerformance > 0.8f) RenderQuality.HIGH else baseQuality
                else -> baseQuality
            }
        }

        return baseQuality
    }

    /**
     * Downgrades quality for performance
     */
    private fun downgradeQuality(quality: RenderQuality): RenderQuality {
        return when (quality) {
            RenderQuality.ULTRA -> RenderQuality.HIGH
            RenderQuality.HIGH -> RenderQuality.MEDIUM
            RenderQuality.MEDIUM -> RenderQuality.LOW
            RenderQuality.LOW -> RenderQuality.LOW
        }
    }

    /**
     * Applies priority-based quality adjustments
     */
    private fun applyPriorityAdjustments(quality: RenderQuality, priority: RenderPriority): RenderQuality {
        return when (priority) {
            RenderPriority.IMMEDIATE -> quality // Keep original quality
            RenderPriority.HIGH -> quality // Keep original quality
            RenderPriority.NORMAL -> when (quality) {
                RenderQuality.ULTRA -> RenderQuality.HIGH
                else -> quality
            }
            RenderPriority.LOW -> when (quality) {
                RenderQuality.ULTRA -> RenderQuality.MEDIUM
                RenderQuality.HIGH -> RenderQuality.MEDIUM
                else -> quality
            }
            RenderPriority.BACKGROUND -> RenderQuality.LOW // Always use low quality for background
        }
    }

    /**
     * Applies context-specific optimizations
     */
    private fun applyContextOptimizations(settings: QualitySettings, context: RenderContext): QualitySettings {
        var optimizedSettings = settings

        // Adjust scale factor based on page size
        val pageArea = context.pageWidth * context.pageHeight * context.zoomLevel * context.zoomLevel
        if (pageArea > 4_000_000) { // Very large render area
            optimizedSettings = optimizedSettings.copy(
                scaleFactor = optimizedSettings.scaleFactor * 0.8f,
                bitmapConfig = if (optimizedSettings.bitmapConfig == Bitmap.Config.ARGB_8888)
                    Bitmap.Config.RGB_565 else optimizedSettings.bitmapConfig
            )
        }

        // Disable expensive features for background rendering
        if (context.priority == RenderPriority.BACKGROUND) {
            optimizedSettings = optimizedSettings.copy(
                enableAntialiasing = false,
                enableSubpixelText = false,
                useHighQualityScaling = false
            )
        }

        return optimizedSettings
    }

    /**
     * Creates optimized Paint object for given quality settings
     */
    fun createOptimizedPaint(settings: QualitySettings): Paint {
        return Paint().apply {
            isAntiAlias = settings.enableAntialiasing
            isSubpixelText = settings.enableSubpixelText
            isFilterBitmap = settings.enableFiltering
            isDither = settings.renderQuality != RenderQuality.LOW

            if (settings.useHighQualityScaling) {
                flags = flags or Paint.FILTER_BITMAP_FLAG
            }
        }
    }

    /**
     * Calculates optimal bitmap size for rendering
     */
    fun calculateOptimalBitmapSize(
        originalWidth: Float,
        originalHeight: Float,
        zoomLevel: Float,
        settings: QualitySettings
    ): Pair<Int, Int> {
        val scaleFactor = settings.scaleFactor * zoomLevel * settings.renderQuality.getScaleFactor()

        val targetWidth = (originalWidth * scaleFactor).toInt()
        val targetHeight = (originalHeight * scaleFactor).toInt()

        // Ensure minimum size
        val minWidth = max(1, targetWidth)
        val minHeight = max(1, targetHeight)

        // Apply maximum size constraints to prevent OOM
        val maxSize = getMaxBitmapSize()
        val constrainedWidth = min(minWidth, maxSize)
        val constrainedHeight = min(minHeight, maxSize)

        return Pair(constrainedWidth, constrainedHeight)
    }

    /**
     * Updates performance metrics
     */
    fun updatePerformanceMetrics(renderTimeMs: Long, memoryUsageMB: Float) {
        lastRenderTime = renderTimeMs

        // Update rendering performance (lower time = better performance)
        val targetRenderTime = 100L // 100ms target
        renderingPerformance = (targetRenderTime.toFloat() / max(renderTimeMs, 1L)).coerceIn(0f, 1f)

        // Update memory pressure
        val maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024) // MB
        memoryPressure = (memoryUsageMB / maxMemory).coerceIn(0f, 1f)

        // Auto-enable performance mode if needed
        if (memoryPressure > 0.8f || renderingPerformance < 0.3f) {
            performanceMode = true
        } else if (memoryPressure < 0.5f && renderingPerformance > 0.7f) {
            performanceMode = false
        }
    }

    /**
     * Sets rendering quality manually
     */
    fun setRenderQuality(quality: RenderQuality) {
        currentQuality = quality
        configuration = configuration.copy(renderQuality = quality)
    }

    /**
     * Enables or disables adaptive quality
     */
    fun setAdaptiveQualityEnabled(enabled: Boolean) {
        adaptiveQualityEnabled = enabled
    }

    /**
     * Enables or disables performance mode
     */
    fun setPerformanceMode(enabled: Boolean) {
        performanceMode = enabled
    }

    /**
     * Gets maximum bitmap size based on device capabilities
     */
    private fun getMaxBitmapSize(): Int {
        return try {
            // Try to get OpenGL texture size limit
            val maxTextureSize = 4096 // Conservative default
            min(maxTextureSize, 8192) // Cap at 8K
        } catch (e: Exception) {
            2048 // Safe fallback
        }
    }

    /**
     * Calculates memory usage for a bitmap
     */
    fun calculateMemoryUsage(width: Int, height: Int, config: Bitmap.Config): Long {
        val bytesPerPixel = when (config) {
            Bitmap.Config.ARGB_8888 -> 4
            Bitmap.Config.RGB_565 -> 2
            Bitmap.Config.ARGB_4444 -> 2
            Bitmap.Config.ALPHA_8 -> 1
            else -> 4
        }
        return width.toLong() * height.toLong() * bytesPerPixel
    }

    /**
     * Gets current quality statistics
     */
    fun getQualityStatistics(): QualityStatistics {
        return QualityStatistics(
            currentQuality = currentQuality,
            adaptiveQualityEnabled = adaptiveQualityEnabled,
            performanceMode = performanceMode,
            memoryPressure = memoryPressure,
            renderingPerformance = renderingPerformance,
            lastRenderTime = lastRenderTime,
            maxBitmapSize = getMaxBitmapSize()
        )
    }

    data class QualityStatistics(
        val currentQuality: RenderQuality,
        val adaptiveQualityEnabled: Boolean,
        val performanceMode: Boolean,
        val memoryPressure: Float,
        val renderingPerformance: Float,
        val lastRenderTime: Long,
        val maxBitmapSize: Int
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Quality Manager Statistics:")
                appendLine("  Current Quality: $currentQuality")
                appendLine("  Adaptive Quality: $adaptiveQualityEnabled")
                appendLine("  Performance Mode: $performanceMode")
                appendLine("  Memory Pressure: ${(memoryPressure * 100).toInt()}%")
                appendLine("  Rendering Performance: ${(renderingPerformance * 100).toInt()}%")
                appendLine("  Last Render Time: ${lastRenderTime}ms")
                appendLine("  Max Bitmap Size: ${maxBitmapSize}px")
            }
        }
    }
}