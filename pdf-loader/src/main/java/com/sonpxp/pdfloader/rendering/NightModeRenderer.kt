package com.sonpxp.pdfloader.rendering


import android.graphics.*
import com.sonpxp.pdfloader.model.Configuration
import androidx.core.graphics.createBitmap

/**
 * Handles night mode and dark theme rendering for PDF content
 * Provides color inversion, contrast adjustment, and eye-friendly display
 */
class NightModeRenderer(
    private val configuration: Configuration,
) {

    enum class NightModeType {
        DISABLED,           // Normal colors
        INVERT,            // Simple color inversion
        SEPIA,             // Sepia tone filter
        DARK_BACKGROUND,   // Dark background with adjusted text
        CUSTOM             // Custom color scheme
    }

    data class NightModeConfig(
        val type: NightModeType,
        val brightness: Float,      // 0.0 to 1.0
        val contrast: Float,        // 0.0 to 2.0
        val warmth: Float,          // 0.0 to 1.0 (blue light filter)
        val backgroundColor: Int,
        val textColor: Int,
        val preserveImages: Boolean,
    )

    private var currentMode = NightModeType.DISABLED
    private var customConfig: NightModeConfig? = null

    // Predefined color matrices
    private val invertMatrix = ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    ))

    private val sepiaMatrix = ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))

    /**
     * Creates night mode Paint for rendering
     */
    fun createNightModePaint(): Paint {
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        if (isNightModeEnabled()) {
            val colorFilter = createColorFilter()
            paint.colorFilter = colorFilter
        }

        return paint
    }

    /**
     * Applies night mode to a bitmap
     */
    fun applyNightMode(source: Bitmap): Bitmap? {
        if (!isNightModeEnabled() || source.isRecycled) {
            return source
        }

        try {
            val result =
                createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)

            val canvas = Canvas(result)
            val paint = createNightModePaint()

            canvas.drawBitmap(source, 0f, 0f, paint)

            return result
        } catch (e: OutOfMemoryError) {
            return source
        }
    }

    /**
     * Applies night mode to canvas for drawing
     */
    fun applyCanvasNightMode(canvas: Canvas) {
        if (isNightModeEnabled()) {
            val colorFilter = createColorFilter()
            val drawFilter = PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG)
            canvas.drawFilter = drawFilter

            // Apply background if dark background mode
            if (currentMode == NightModeType.DARK_BACKGROUND) {
                val config = getNightModeConfig()
                canvas.drawColor(config.backgroundColor)
            }
        }
    }

    /**
     * Creates color filter based on current night mode
     */
    private fun createColorFilter(): ColorFilter? {
        val config = getNightModeConfig()

        return when (config.type) {
            NightModeType.DISABLED -> null

            NightModeType.INVERT -> {
                val matrix = ColorMatrix(invertMatrix)
                adjustMatrix(matrix, config)
                ColorMatrixColorFilter(matrix)
            }

            NightModeType.SEPIA -> {
                val matrix = ColorMatrix(sepiaMatrix)
                adjustMatrix(matrix, config)
                ColorMatrixColorFilter(matrix)
            }

            NightModeType.DARK_BACKGROUND -> {
                createDarkBackgroundFilter(config)
            }

            NightModeType.CUSTOM -> {
                customConfig?.let { createCustomFilter(it) }
            }
        }
    }

    /**
     * Creates dark background color filter
     */
    private fun createDarkBackgroundFilter(config: NightModeConfig): ColorFilter {
        // Create a matrix that darkens backgrounds and adjusts text
        val matrix = ColorMatrix(floatArrayOf(
            0.8f, 0f, 0f, 0f, -50f,    // Reduce red, darken
            0f, 0.8f, 0f, 0f, -50f,    // Reduce green, darken
            0f, 0f, 0.8f, 0f, -50f,    // Reduce blue, darken
            0f, 0f, 0f, 1f, 0f         // Keep alpha
        ))

        adjustMatrix(matrix, config)
        return ColorMatrixColorFilter(matrix)
    }

    /**
     * Creates custom color filter
     */
    private fun createCustomFilter(config: NightModeConfig): ColorFilter {
        val matrix = ColorMatrix()

        // Apply brightness
        val brightness = (config.brightness - 0.5f) * 255f
        matrix.set(floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))

        // Apply contrast
        val contrast = config.contrast
        val translate = (1f - contrast) / 2f * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.postConcat(contrastMatrix)

        // Apply warmth (reduce blue light)
        if (config.warmth > 0f) {
            val warmthMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f - config.warmth * 0.3f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(warmthMatrix)
        }

        return ColorMatrixColorFilter(matrix)
    }

    /**
     * Adjusts color matrix based on configuration
     */
    private fun adjustMatrix(matrix: ColorMatrix, config: NightModeConfig) {
        // Apply brightness adjustment
        if (config.brightness != 0.5f) {
            val brightness = (config.brightness - 0.5f) * 100f
            val brightnessMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(brightnessMatrix)
        }

        // Apply contrast adjustment
        if (config.contrast != 1f) {
            val contrast = config.contrast
            val translate = (1f - contrast) / 2f * 255f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(contrastMatrix)
        }

        // Apply warmth (blue light filter)
        if (config.warmth > 0f) {
            val warmthMatrix = ColorMatrix(floatArrayOf(
                1f + config.warmth * 0.1f, 0f, 0f, 0f, 0f,
                0f, 1f + config.warmth * 0.05f, 0f, 0f, 0f,
                0f, 0f, 1f - config.warmth * 0.3f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(warmthMatrix)
        }
    }

    /**
     * Gets night mode configuration for current mode
     */
    private fun getNightModeConfig(): NightModeConfig {
        return customConfig ?: when (currentMode) {
            NightModeType.DISABLED -> NightModeConfig(
                type = NightModeType.DISABLED,
                brightness = 0.5f,
                contrast = 1f,
                warmth = 0f,
                backgroundColor = Color.WHITE,
                textColor = Color.BLACK,
                preserveImages = false
            )

            NightModeType.INVERT -> NightModeConfig(
                type = NightModeType.INVERT,
                brightness = 0.6f,
                contrast = 1.1f,
                warmth = 0.2f,
                backgroundColor = Color.BLACK,
                textColor = Color.WHITE,
                preserveImages = true
            )

            NightModeType.SEPIA -> NightModeConfig(
                type = NightModeType.SEPIA,
                brightness = 0.4f,
                contrast = 1.2f,
                warmth = 0.6f,
                backgroundColor = Color.rgb(240, 230, 200),
                textColor = Color.rgb(80, 60, 40),
                preserveImages = false
            )

            NightModeType.DARK_BACKGROUND -> NightModeConfig(
                type = NightModeType.DARK_BACKGROUND,
                brightness = 0.3f,
                contrast = 1.3f,
                warmth = 0.3f,
                backgroundColor = Color.rgb(40, 40, 40),
                textColor = Color.rgb(220, 220, 220),
                preserveImages = true
            )

            NightModeType.CUSTOM -> customConfig ?: getNightModeConfig()
        }
    }

    /**
     * Sets night mode type
     */
    fun setNightModeType(type: NightModeType) {
        currentMode = type
    }

    /**
     * Sets custom night mode configuration
     */
    fun setCustomConfig(config: NightModeConfig) {
        customConfig = config
        currentMode = NightModeType.CUSTOM
    }

    /**
     * Gets current night mode type
     */
    fun getNightModeType(): NightModeType = currentMode

    /**
     * Checks if night mode is enabled
     */
    fun isNightModeEnabled(): Boolean {
        return configuration.nightMode && currentMode != NightModeType.DISABLED
    }

    /**
     * Creates night mode compatible background color
     */
    fun getBackgroundColor(): Int {
        return if (isNightModeEnabled()) {
            getNightModeConfig().backgroundColor
        } else {
            Color.WHITE
        }
    }

    /**
     * Creates night mode compatible text color
     */
    fun getTextColor(): Int {
        return if (isNightModeEnabled()) {
            getNightModeConfig().textColor
        } else {
            Color.BLACK
        }
    }

    /**
     * Adjusts color for night mode
     */
    fun adjustColor(originalColor: Int): Int {
        if (!isNightModeEnabled()) {
            return originalColor
        }

        val config = getNightModeConfig()

        return when (config.type) {
            NightModeType.INVERT -> {
                val r = 255 - Color.red(originalColor)
                val g = 255 - Color.green(originalColor)
                val b = 255 - Color.blue(originalColor)
                val a = Color.alpha(originalColor)
                Color.argb(a, r, g, b)
            }

            NightModeType.DARK_BACKGROUND -> {
                // Make light colors darker and dark colors lighter
                val brightness = (Color.red(originalColor) + Color.green(originalColor) + Color.blue(originalColor)) / 3f / 255f
                if (brightness > 0.5f) {
                    // Light color - make it darker
                    val factor = 0.3f
                    val r = (Color.red(originalColor) * factor).toInt()
                    val g = (Color.green(originalColor) * factor).toInt()
                    val b = (Color.blue(originalColor) * factor).toInt()
                    Color.argb(Color.alpha(originalColor), r, g, b)
                } else {
                    // Dark color - make it lighter
                    val factor = 1.5f
                    val r = (Color.red(originalColor) * factor).toInt().coerceAtMost(255)
                    val g = (Color.green(originalColor) * factor).toInt().coerceAtMost(255)
                    val b = (Color.blue(originalColor) * factor).toInt().coerceAtMost(255)
                    Color.argb(Color.alpha(originalColor), r, g, b)
                }
            }

            else -> originalColor
        }
    }

    /**
     * Creates night mode preset configurations
     */
    fun createPresetConfig(preset: String): NightModeConfig? {
        return when (preset.lowercase()) {
            "reading" -> NightModeConfig(
                type = NightModeType.DARK_BACKGROUND,
                brightness = 0.25f,
                contrast = 1.4f,
                warmth = 0.4f,
                backgroundColor = Color.rgb(30, 30, 30),
                textColor = Color.rgb(230, 230, 230),
                preserveImages = true
            )

            "amber" -> NightModeConfig(
                type = NightModeType.CUSTOM,
                brightness = 0.35f,
                contrast = 1.2f,
                warmth = 0.8f,
                backgroundColor = Color.rgb(50, 40, 20),
                textColor = Color.rgb(255, 200, 100),
                preserveImages = true
            )

            "blue_light_filter" -> NightModeConfig(
                type = NightModeType.CUSTOM,
                brightness = 0.5f,
                contrast = 1f,
                warmth = 0.6f,
                backgroundColor = Color.WHITE,
                textColor = Color.BLACK,
                preserveImages = false
            )

            else -> null
        }
    }

    /**
     * Gets night mode statistics
     */
    fun getStatistics(): NightModeStatistics {
        val config = getNightModeConfig()

        return NightModeStatistics(
            currentMode = currentMode,
            isEnabled = isNightModeEnabled(),
            configurationEnabled = configuration.nightMode,
            brightness = config.brightness,
            contrast = config.contrast,
            warmth = config.warmth,
            backgroundColor = config.backgroundColor,
            textColor = config.textColor,
            preserveImages = config.preserveImages
        )
    }

    data class NightModeStatistics(
        val currentMode: NightModeType,
        val isEnabled: Boolean,
        val configurationEnabled: Boolean,
        val brightness: Float,
        val contrast: Float,
        val warmth: Float,
        val backgroundColor: Int,
        val textColor: Int,
        val preserveImages: Boolean,
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Night Mode Renderer Statistics:")
                appendLine("  Current Mode: $currentMode")
                appendLine("  Enabled: $isEnabled")
                appendLine("  Config Enabled: $configurationEnabled")
                appendLine("  Brightness: ${(brightness * 100).toInt()}%")
                appendLine("  Contrast: ${String.format("%.1f", contrast)}")
                appendLine("  Warmth: ${(warmth * 100).toInt()}%")
                appendLine("  Background: #${Integer.toHexString(backgroundColor).uppercase()}")
                appendLine("  Text Color: #${Integer.toHexString(textColor).uppercase()}")
                appendLine("  Preserve Images: $preserveImages")
            }
        }
    }
}