package com.sonpxp.pdfloader.core

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sonpxp.pdfloader.RenderQuality
import com.sonpxp.pdfloader.model.PageInfo
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Asynchronous task for rendering PDF pages
 * Handles the rendering operation on a background thread
 */
class RenderTask(
    private val pageRenderer: PageRenderer,
    private val taskId: String,
    private val priority: Priority = Priority.NORMAL,
) : Callable<RenderResult> {

    companion object {
        private const val TAG = "RenderTask"
    }

    private val isCancelled = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressCallback: ((Float) -> Unit)? = null
    private var resultCallback: ((RenderResult) -> Unit)? = null
    private var errorCallback: ((Exception) -> Unit)? = null

    // Task parameters
    private var pdfPage: PdfRenderer.Page? = null
    private var pageNumber: Int = 0
    private var targetWidth: Int = 0
    private var targetHeight: Int = 0
    private var quality: RenderQuality = RenderQuality.MEDIUM
    private var backgroundColor: Int = android.graphics.Color.WHITE
    private var nightMode: Boolean = false
    private var antialiasing: Boolean = true
    private var renderAnnotations: Boolean = true
    private var sourceRect: RectF? = null // For partial rendering
    private var renderType: RenderType = RenderType.FULL_PAGE

    /**
     * Types of rendering operations
     */
    enum class RenderType {
        FULL_PAGE,
        PAGE_PORTION,
        THUMBNAIL
    }

    /**
     * Task priority levels
     */
    enum class Priority(val value: Int) {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        URGENT(4);

        companion object {
            fun compare(p1: Priority, p2: Priority): Int = p2.value - p1.value
        }
    }

    /**
     * Configures the task for full page rendering
     */
    fun configureFullPageRender(
        pdfPage: PdfRenderer.Page,
        pageNumber: Int,
        targetWidth: Int,
        targetHeight: Int,
        quality: RenderQuality = RenderQuality.MEDIUM,
        backgroundColor: Int = android.graphics.Color.WHITE,
        nightMode: Boolean = false,
        antialiasing: Boolean = true,
        renderAnnotations: Boolean = true,
    ): RenderTask {
        this.pdfPage = pdfPage
        this.pageNumber = pageNumber
        this.targetWidth = targetWidth
        this.targetHeight = targetHeight
        this.quality = quality
        this.backgroundColor = backgroundColor
        this.nightMode = nightMode
        this.antialiasing = antialiasing
        this.renderAnnotations = renderAnnotations
        this.renderType = RenderType.FULL_PAGE
        return this
    }

    /**
     * Configures the task for partial page rendering
     */
    fun configurePartialRender(
        pdfPage: PdfRenderer.Page,
        pageNumber: Int,
        sourceRect: RectF,
        targetWidth: Int,
        targetHeight: Int,
        quality: RenderQuality = RenderQuality.MEDIUM,
        backgroundColor: Int = android.graphics.Color.WHITE,
        nightMode: Boolean = false,
        antialiasing: Boolean = true,
    ): RenderTask {
        this.pdfPage = pdfPage
        this.pageNumber = pageNumber
        this.sourceRect = sourceRect
        this.targetWidth = targetWidth
        this.targetHeight = targetHeight
        this.quality = quality
        this.backgroundColor = backgroundColor
        this.nightMode = nightMode
        this.antialiasing = antialiasing
        this.renderAnnotations = true // Always render annotations for portions
        this.renderType = RenderType.PAGE_PORTION
        return this
    }

    /**
     * Configures the task for thumbnail rendering
     */
    fun configureThumbnailRender(
        pdfPage: PdfRenderer.Page,
        pageNumber: Int,
        maxSize: Int = 200,
        backgroundColor: Int = android.graphics.Color.WHITE,
    ): RenderTask {
        this.pdfPage = pdfPage
        this.pageNumber = pageNumber
        this.targetWidth = maxSize
        this.targetHeight = maxSize
        this.quality = RenderQuality.LOW
        this.backgroundColor = backgroundColor
        this.nightMode = false
        this.antialiasing = true
        this.renderAnnotations = false
        this.renderType = RenderType.THUMBNAIL
        return this
    }

    /**
     * Sets progress callback (called on main thread)
     */
    fun onProgress(callback: (Float) -> Unit): RenderTask {
        this.progressCallback = callback
        return this
    }

    /**
     * Sets result callback (called on main thread)
     */
    fun onResult(callback: (RenderResult) -> Unit): RenderTask {
        this.resultCallback = callback
        return this
    }

    /**
     * Sets error callback (called on main thread)
     */
    fun onError(callback: (Exception) -> Unit): RenderTask {
        this.errorCallback = callback
        return this
    }

    /**
     * Executes the rendering task
     */
    override fun call(): RenderResult {
        if (isCancelled.get()) {
            return RenderResult.cancelled(taskId)
        }

        val startTime = System.currentTimeMillis()

        try {
            reportProgress(0.1f) // Task started

            val page = pdfPage ?: throw IllegalStateException("PDF page not configured")

            reportProgress(0.2f) // Validation complete

            val renderedResult = when (renderType) {
                RenderType.FULL_PAGE -> {
                    reportProgress(0.3f)
                    pageRenderer.renderPage(
                        pdfPage = page,
                        pageNumber = pageNumber,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        quality = quality,
                        backgroundColor = backgroundColor,
                        nightMode = nightMode,
                        antialiasing = antialiasing,
                        renderAnnotations = renderAnnotations
                    )
                }

                RenderType.PAGE_PORTION -> {
                    reportProgress(0.3f)
                    val rect = sourceRect
                        ?: throw IllegalStateException("Source rect not configured for portion render")
                    pageRenderer.renderPagePortion(
                        pdfPage = page,
                        pageNumber = pageNumber,
                        sourceRect = rect,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        quality = quality,
                        backgroundColor = backgroundColor,
                        nightMode = nightMode,
                        antialiasing = antialiasing
                    )
                }

                RenderType.THUMBNAIL -> {
                    reportProgress(0.3f)
                    pageRenderer.renderThumbnail(
                        pdfPage = page,
                        pageNumber = pageNumber,
                        maxSize = targetWidth,
                        backgroundColor = backgroundColor
                    )
                }
            }

            if (isCancelled.get()) {
                // Clean up if cancelled during rendering
                renderedResult.bitmap.recycle()
                return RenderResult.cancelled(taskId)
            }

            reportProgress(0.9f) // Rendering complete

            val totalTime = System.currentTimeMillis() - startTime
            val result = RenderResult.success(
                taskId = taskId,
                bitmap = renderedResult.bitmap,
                pageInfo = renderedResult.pageInfo,
                renderTimeMs = totalTime,
                renderType = renderType,
                quality = quality
            )

            reportProgress(1.0f) // Task complete

            // Notify success callback on main thread
            mainHandler.post {
                if (!isCancelled.get()) {
                    resultCallback?.invoke(result)
                }
            }

            Log.d(TAG, "Render task completed: $taskId in ${totalTime}ms")
            return result

        } catch (e: Exception) {
            val errorResult = RenderResult.error(taskId, e)

            // Notify error callback on main thread
            mainHandler.post {
                if (!isCancelled.get()) {
                    errorCallback?.invoke(e)
                }
            }

            Log.e(TAG, "Render task failed: $taskId", e)
            return errorResult
        }
    }

    /**
     * Cancels the rendering task
     */
    fun cancel() {
        isCancelled.set(true)
        Log.d(TAG, "Render task cancelled: $taskId")
    }

    /**
     * Checks if the task is cancelled
     */
    fun isCancelled(): Boolean = isCancelled.get()

    /**
     * Gets the task ID
     */
    fun getTaskId(): String = taskId

    /**
     * Gets the task priority
     */
    fun getPriority(): Priority = priority

    /**
     * Gets the page number being rendered
     */
    fun getPageNumber(): Int = pageNumber

    /**
     * Gets task information for debugging
     */
    fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskId = taskId,
            pageNumber = pageNumber,
            renderType = renderType,
            priority = priority,
            targetSize = Pair(targetWidth, targetHeight),
            quality = quality,
            isCancelled = isCancelled.get()
        )
    }

    private fun reportProgress(progress: Float) {
        if (!isCancelled.get() && progressCallback != null) {
            mainHandler.post {
                progressCallback?.invoke(progress)
            }
        }
    }

    /**
     * Task information for debugging and monitoring
     */
    data class TaskInfo(
        val taskId: String,
        val pageNumber: Int,
        val renderType: RenderType,
        val priority: Priority,
        val targetSize: Pair<Int, Int>,
        val quality: RenderQuality,
        val isCancelled: Boolean,
    ) {
        override fun toString(): String {
            return "RenderTask[$taskId]: Page $pageNumber, $renderType, " +
                    "${targetSize.first}x${targetSize.second}, $quality, " +
                    "Priority: $priority, Cancelled: $isCancelled"
        }
    }
}

/**
 * Result of a render task execution
 */
sealed class RenderResult(
    val taskId: String,
    val isSuccess: Boolean,
    val renderTimeMs: Long,
) {

    /**
     * Successful render result
     */
    class Success(
        taskId: String,
        val bitmap: Bitmap,
        val pageInfo: PageInfo,
        renderTimeMs: Long,
        val renderType: RenderTask.RenderType,
        val quality: RenderQuality,
    ) : RenderResult(taskId, true, renderTimeMs) {

        fun getRenderEfficiency(): Float {
            val totalPixels = bitmap.width * bitmap.height
            return if (renderTimeMs > 0) totalPixels.toFloat() / renderTimeMs.toFloat() else 0f
        }
    }

    /**
     * Failed render result
     */
    class Error(
        taskId: String,
        val exception: Exception,
        renderTimeMs: Long = 0,
    ) : RenderResult(taskId, false, renderTimeMs)

    /**
     * Cancelled render result
     */
    class Cancelled(
        taskId: String,
        renderTimeMs: Long = 0,
    ) : RenderResult(taskId, false, renderTimeMs)

    companion object {
        fun success(
            taskId: String,
            bitmap: Bitmap,
            pageInfo: PageInfo,
            renderTimeMs: Long,
            renderType: RenderTask.RenderType,
            quality: RenderQuality,
        ): Success {
            return Success(taskId, bitmap, pageInfo, renderTimeMs, renderType, quality)
        }

        fun error(taskId: String, exception: Exception, renderTimeMs: Long = 0): Error {
            return Error(taskId, exception, renderTimeMs)
        }

        fun cancelled(taskId: String, renderTimeMs: Long = 0): Cancelled {
            return Cancelled(taskId, renderTimeMs)
        }
    }
}