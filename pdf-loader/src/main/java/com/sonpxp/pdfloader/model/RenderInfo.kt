package com.sonpxp.pdfloader.model

import com.sonpxp.pdfloader.RenderQuality
import android.graphics.Bitmap
import android.graphics.RectF
import com.sonpxp.pdfloader.RenderTaskState

/**
 * Contains comprehensive information about a page rendering operation
 */
data class RenderInfo(
    /** Page number being rendered (0-indexed) */
    val pageNumber: Int,

    /** Render task ID for tracking */
    val taskId: String,

    /** Current render state */
    val state: RenderTaskState,

    /** Target render dimensions */
    val targetWidth: Int,
    val targetHeight: Int,

    /** Actual render dimensions (may differ due to scaling) */
    val actualWidth: Int,
    val actualHeight: Int,

    /** Render quality setting used */
    val quality: RenderQuality,

    /** Zoom level for this render */
    val zoomLevel: Float,

    /** Source rectangle being rendered (null for full page) */
    val sourceRect: RectF? = null,

    /** Whether this is a thumbnail render */
    val isThumbnail: Boolean = false,

    /** Render start timestamp */
    val startTime: Long = System.currentTimeMillis(),

    /** Render completion timestamp (0 if not completed) */
    val endTime: Long = 0,

    /** Total render time in milliseconds */
    val renderTimeMs: Long = if (endTime > 0) endTime - startTime else 0,

    /** Memory used for this render in bytes */
    val memoryUsed: Long = 0,

    /** Whether result was served from cache */
    val fromCache: Boolean = false,

    /** Error information if render failed */
    val error: Exception? = null,

    /** Additional render properties */
    val properties: Map<String, Any> = emptyMap()
) {

    /**
     * Checks if the render operation is complete
     */
    fun isComplete(): Boolean {
        return state == RenderTaskState.COMPLETED ||
                state == RenderTaskState.FAILED ||
                state == RenderTaskState.CANCELLED
    }

    /**
     * Checks if the render was successful
     */
    fun isSuccess(): Boolean {
        return state == RenderTaskState.COMPLETED && error == null
    }

    /**
     * Gets render efficiency (pixels per millisecond)
     */
    fun getRenderEfficiency(): Float {
        val totalPixels = actualWidth * actualHeight
        return if (renderTimeMs > 0) totalPixels.toFloat() / renderTimeMs.toFloat() else 0f
    }

    /**
     * Gets the scale factor used for rendering
     */
    fun getScaleFactor(): Float {
        return quality.getScaleFactor()
    }

    /**
     * Gets the render area in square pixels
     */
    fun getRenderArea(): Int {
        return actualWidth * actualHeight
    }

    /**
     * Creates a copy with updated state
     */
    fun withState(newState: RenderTaskState, timestamp: Long = System.currentTimeMillis()): RenderInfo {
        return when (newState) {
            RenderTaskState.COMPLETED -> copy(
                state = newState,
                endTime = timestamp,
                renderTimeMs = timestamp - startTime
            )
            RenderTaskState.FAILED -> copy(
                state = newState,
                endTime = timestamp,
                renderTimeMs = timestamp - startTime
            )
            RenderTaskState.CANCELLED -> copy(
                state = newState,
                endTime = timestamp,
                renderTimeMs = timestamp - startTime
            )
            else -> copy(state = newState)
        }
    }

    /**
     * Creates a copy with error information
     */
    fun withError(exception: Exception): RenderInfo {
        return copy(
            state = RenderTaskState.FAILED,
            error = exception,
            endTime = System.currentTimeMillis(),
            renderTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Creates a copy with memory usage information
     */
    fun withMemoryUsage(bytes: Long): RenderInfo {
        return copy(memoryUsed = bytes)
    }

    /**
     * Creates a copy marking as served from cache
     */
    fun withCacheHit(): RenderInfo {
        return copy(
            fromCache = true,
            state = RenderTaskState.COMPLETED,
            endTime = startTime, // Immediate for cache hits
            renderTimeMs = 0
        )
    }

    /**
     * Gets a human-readable summary of the render info
     */
    fun getSummary(): String {
        val status = when (state) {
            RenderTaskState.PENDING -> "Pending"
            RenderTaskState.RUNNING -> "Running"
            RenderTaskState.COMPLETED -> if (fromCache) "Cached" else "Rendered"
            RenderTaskState.FAILED -> "Failed"
            RenderTaskState.CANCELLED -> "Cancelled"
        }

        val sizeInfo = "${actualWidth}x${actualHeight}"
        val timeInfo = if (renderTimeMs > 0) " in ${renderTimeMs}ms" else ""
        val qualityInfo = quality.name.lowercase()

        return "Page $pageNumber: $status ($sizeInfo, $qualityInfo)$timeInfo"
    }

    /**
     * Gets detailed render information for debugging
     */
    fun getDetailedInfo(): String {
        return buildString {
            appendLine("Render Info for Page $pageNumber:")
            appendLine("  Task ID: $taskId")
            appendLine("  State: $state")
            appendLine("  Target Size: ${targetWidth}x${targetHeight}")
            appendLine("  Actual Size: ${actualWidth}x${actualHeight}")
            appendLine("  Quality: $quality (${getScaleFactor()}x)")
            appendLine("  Zoom Level: ${String.format("%.2f", zoomLevel)}")
            appendLine("  Render Time: ${renderTimeMs}ms")
            appendLine("  Memory Used: ${formatBytes(memoryUsed)}")
            appendLine("  From Cache: $fromCache")
            appendLine("  Efficiency: ${String.format("%.1f", getRenderEfficiency())} px/ms")

            sourceRect?.let {
                appendLine("  Source Rect: (${it.left}, ${it.top}) - (${it.right}, ${it.bottom})")
            }

            if (isThumbnail) {
                appendLine("  Type: Thumbnail")
            }

            error?.let {
                appendLine("  Error: ${it.message}")
            }

            if (properties.isNotEmpty()) {
                appendLine("  Properties:")
                properties.forEach { (key, value) ->
                    appendLine("    $key: $value")
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }

    companion object {
        /**
         * Creates a RenderInfo for a new render task
         */
        fun create(
            pageNumber: Int,
            taskId: String,
            targetWidth: Int,
            targetHeight: Int,
            quality: RenderQuality,
            zoomLevel: Float,
            sourceRect: RectF? = null,
            isThumbnail: Boolean = false
        ): RenderInfo {
            val scaleFactor = quality.getScaleFactor()
            val actualWidth = (targetWidth * scaleFactor).toInt()
            val actualHeight = (targetHeight * scaleFactor).toInt()

            return RenderInfo(
                pageNumber = pageNumber,
                taskId = taskId,
                state = RenderTaskState.PENDING,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                actualWidth = actualWidth,
                actualHeight = actualHeight,
                quality = quality,
                zoomLevel = zoomLevel,
                sourceRect = sourceRect,
                isThumbnail = isThumbnail
            )
        }

        /**
         * Creates a RenderInfo for a cache hit
         */
        fun createCacheHit(
            pageNumber: Int,
            taskId: String,
            bitmap: Bitmap,
            quality: RenderQuality,
            zoomLevel: Float
        ): RenderInfo {
            return RenderInfo(
                pageNumber = pageNumber,
                taskId = taskId,
                state = RenderTaskState.COMPLETED,
                targetWidth = bitmap.width,
                targetHeight = bitmap.height,
                actualWidth = bitmap.width,
                actualHeight = bitmap.height,
                quality = quality,
                zoomLevel = zoomLevel,
                fromCache = true,
                renderTimeMs = 0,
                memoryUsed = bitmap.allocationByteCount.toLong()
            )
        }
    }
}