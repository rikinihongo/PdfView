package com.sonpxp.pdfloader.utils


import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.*

/**
 * Utility class for mathematical calculations used in PDF viewer
 */
object MathUtils {

    private const val EPSILON = 1e-6f

    /**
     * Clamps a value between min and max
     */
    @JvmStatic
    fun clamp(value: Float, min: Float, max: Float): Float {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    /**
     * Clamps an integer value between min and max
     */
    @JvmStatic
    fun clamp(value: Int, min: Int, max: Int): Int {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    /**
     * Linear interpolation between two values
     */
    @JvmStatic
    fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    /**
     * Inverse linear interpolation - gets the fraction for a value between start and end
     */
    @JvmStatic
    fun invLerp(start: Float, end: Float, value: Float): Float {
        return if (abs(end - start) < EPSILON) 0f else (value - start) / (end - start)
    }

    /**
     * Smoothstep interpolation (smoother than linear)
     */
    @JvmStatic
    fun smoothstep(start: Float, end: Float, fraction: Float): Float {
        val t = clamp((fraction - start) / (end - start), 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Checks if two floats are approximately equal
     */
    @JvmStatic
    fun approximately(a: Float, b: Float, epsilon: Float = EPSILON): Boolean {
        return abs(a - b) < epsilon
    }

    /**
     * Calculates distance between two points
     */
    @JvmStatic
    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculates distance between two PointF objects
     */
    @JvmStatic
    fun distance(p1: PointF, p2: PointF): Float {
        return distance(p1.x, p1.y, p2.x, p2.y)
    }

    /**
     * Calculates squared distance (faster when you don't need exact distance)
     */
    @JvmStatic
    fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return dx * dx + dy * dy
    }

    /**
     * Normalizes an angle to be between 0 and 360 degrees
     */
    @JvmStatic
    fun normalizeAngle(angle: Float): Float {
        var result = angle % 360f
        if (result < 0) result += 360f
        return result
    }

    /**
     * Converts degrees to radians
     */
    @JvmStatic
    fun toRadians(degrees: Float): Float {
        return degrees * PI.toFloat() / 180f
    }

    /**
     * Converts radians to degrees
     */
    @JvmStatic
    fun toDegrees(radians: Float): Float {
        return radians * 180f / PI.toFloat()
    }

    /**
     * Calculates the scale factor to fit a rectangle inside another rectangle
     */
    @JvmStatic
    fun calculateFitScale(
        contentWidth: Float,
        contentHeight: Float,
        containerWidth: Float,
        containerHeight: Float,
        fitMode: FitMode = FitMode.FIT_INSIDE
    ): Float {
        if (contentWidth <= 0 || contentHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
            return 1f
        }

        val scaleX = containerWidth / contentWidth
        val scaleY = containerHeight / contentHeight

        return when (fitMode) {
            FitMode.FIT_INSIDE -> min(scaleX, scaleY)
            FitMode.FIT_OUTSIDE -> max(scaleX, scaleY)
            FitMode.FIT_WIDTH -> scaleX
            FitMode.FIT_HEIGHT -> scaleY
        }
    }

    /**
     * Calculates the center point to fit content inside container
     */
    @JvmStatic
    fun calculateFitCenter(
        contentWidth: Float,
        contentHeight: Float,
        containerWidth: Float,
        containerHeight: Float,
        scale: Float
    ): PointF {
        val scaledWidth = contentWidth * scale
        val scaledHeight = contentHeight * scale

        val x = (containerWidth - scaledWidth) / 2f
        val y = (containerHeight - scaledHeight) / 2f

        return PointF(x, y)
    }

    /**
     * Rotates a point around another point
     */
    @JvmStatic
    fun rotatePoint(
        pointX: Float,
        pointY: Float,
        centerX: Float,
        centerY: Float,
        angleRadians: Float
    ): PointF {
        val cos = cos(angleRadians)
        val sin = sin(angleRadians)

        val dx = pointX - centerX
        val dy = pointY - centerY

        val rotatedX = dx * cos - dy * sin + centerX
        val rotatedY = dx * sin + dy * cos + centerY

        return PointF(rotatedX, rotatedY)
    }

    /**
     * Calculates the intersection of two rectangles
     */
    @JvmStatic
    fun intersectRects(rect1: RectF, rect2: RectF): RectF? {
        val left = max(rect1.left, rect2.left)
        val top = max(rect1.top, rect2.top)
        val right = min(rect1.right, rect2.right)
        val bottom = min(rect1.bottom, rect2.bottom)

        return if (left < right && top < bottom) {
            RectF(left, top, right, bottom)
        } else {
            null
        }
    }

    /**
     * Calculates the area of a rectangle
     */
    @JvmStatic
    fun rectArea(rect: RectF): Float {
        return rect.width() * rect.height()
    }

    /**
     * Expands a rectangle by a given amount
     */
    @JvmStatic
    fun expandRect(rect: RectF, amount: Float): RectF {
        return RectF(
            rect.left - amount,
            rect.top - amount,
            rect.right + amount,
            rect.bottom + amount
        )
    }

    /**
     * Scales a rectangle around its center
     */
    @JvmStatic
    fun scaleRect(rect: RectF, scale: Float): RectF {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val halfWidth = rect.width() * scale / 2f
        val halfHeight = rect.height() * scale / 2f

        return RectF(
            centerX - halfWidth,
            centerY - halfHeight,
            centerX + halfWidth,
            centerY + halfHeight
        )
    }

    /**
     * Calculates zoom level to fit content in viewport
     */
    @JvmStatic
    fun calculateZoomToFit(
        contentWidth: Float,
        contentHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        maxZoom: Float = Float.MAX_VALUE
    ): Float {
        val scale = calculateFitScale(contentWidth, contentHeight, viewportWidth, viewportHeight)
        return min(scale, maxZoom)
    }

    /**
     * Calculates the optimal zoom level for reading text
     */
    @JvmStatic
    fun calculateReadingZoom(
        pageWidth: Float,
        pageHeight: Float,
        viewportWidth: Float,
        minTextSize: Float = 12f, // minimum readable text size in pixels
        averageTextSize: Float = 10f // average text size in PDF points
    ): Float {
        // Convert PDF points to pixels (assuming 72 DPI)
        val textSizeInPixels = averageTextSize * viewportWidth / pageWidth
        return if (textSizeInPixels < minTextSize) {
            minTextSize / textSizeInPixels
        } else {
            1f
        }
    }

    /**
     * Rounds a value to the nearest step
     */
    @JvmStatic
    fun roundToStep(value: Float, step: Float): Float {
        return if (step > 0) {
            round(value / step) * step
        } else {
            value
        }
    }

    /**
     * Fit modes for scaling calculations
     */
    enum class FitMode {
        /** Scale to fit inside container (maintain aspect ratio) */
        FIT_INSIDE,

        /** Scale to fill container completely (maintain aspect ratio) */
        FIT_OUTSIDE,

        /** Scale to fit width exactly */
        FIT_WIDTH,

        /** Scale to fit height exactly */
        FIT_HEIGHT
    }
}