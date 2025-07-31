package com.sonpxp.pdfloader.core

import java.util.concurrent.CompletableFuture
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.sonpxp.pdfloader.RenderQuality
import com.sonpxp.pdfloader.exception.PageRenderException
import com.sonpxp.pdfloader.model.Configuration
import com.sonpxp.pdfloader.model.PageInfo
import com.sonpxp.pdfloader.source.DocumentSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Main PDF processing engine that coordinates all PDF operations
 * Manages document lifecycle, rendering pipeline, and resource allocation
 */
class PdfEngine(private val context: Context) {

    companion object {
        private const val TAG = "PdfEngine"
        private const val DEFAULT_CACHE_SIZE_MB = 120
        private const val DEFAULT_BITMAP_POOL_SIZE_MB = 50
        private const val DEFAULT_MAX_CONCURRENT_PAGES = 3
    }

    // Core components
    private val bitmapPool = BitmapPool(maxSizeBytes = DEFAULT_BITMAP_POOL_SIZE_MB * 1024 * 1024)
    private val cacheManager = CacheManager(maxCacheSizeMB = DEFAULT_CACHE_SIZE_MB, bitmapPool = bitmapPool)
    private val memoryManager = MemoryManager(context, cacheManager, bitmapPool)
    private val pageRenderer = PageRenderer(bitmapPool, memoryManager)
    private val renderQueue = RenderQueue()

    // Document management
    private var currentDocument: PdfDocument? = null
    private var currentConfiguration: Configuration? = null
    private val isInitialized = AtomicBoolean(false)
    private val isLoading = AtomicBoolean(false)
    private val renderRequestCounter = AtomicInteger(0)

    // Engine listeners
    private val engineListeners = ConcurrentHashMap<String, EngineListener>()

    /**
     * Interface for listening to engine events
     */
    interface EngineListener {
        fun onDocumentLoadStart(source: DocumentSource) {}
        fun onDocumentLoadProgress(progress: Float) {}
        fun onDocumentLoadComplete(pageCount: Int, metadata: PdfDocument.DocumentMetadata) {}
        fun onDocumentLoadError(error: Exception) {}
        fun onPageRenderStart(pageNumber: Int) {}
        fun onPageRenderComplete(pageNumber: Int, renderTime: Long) {}
        fun onPageRenderError(pageNumber: Int, error: Exception) {}
        fun onMemoryWarning(availableMemory: Long, totalMemory: Long) {}
        fun onEngineShutdown() {}
    }

    init {
        // Initialize memory manager listeners
        memoryManager.addMemoryListener(object : MemoryManager.MemoryListener {
            override fun onLowMemoryWarning(availableMemory: Long, totalMemory: Long) {
                notifyEngineListeners { it.onMemoryWarning(availableMemory, totalMemory) }
            }

            override fun onCriticalMemoryWarning(availableMemory: Long, totalMemory: Long) {
                handleCriticalMemory()
                notifyEngineListeners { it.onMemoryWarning(availableMemory, totalMemory) }
            }

            override fun onOutOfMemoryError(requiredMemory: Long, availableMemory: Long) {
                handleOutOfMemory()
            }
        })

        // Initialize render queue listeners
        renderQueue.addQueueListener(object : RenderQueue.QueueListener {
            override fun onTaskCompleted(taskId: String, result: RenderResult) {
                handleRenderTaskCompleted(taskId, result)
            }

            override fun onTaskCancelled(taskId: String) {
                Log.d(TAG, "Render task cancelled: $taskId")
            }
        })

        isInitialized.set(true)
        Log.d(TAG, "PDF Engine initialized successfully")
    }

    /**
     * Loads a PDF document from the specified source
     * @param configuration the document configuration
     * @param progressCallback optional progress callback
     * @return future that completes when document is loaded
     */
    fun loadDocument(
        configuration: Configuration,
        progressCallback: ((Float) -> Unit)? = null
    ): Future<LoadResult> {

        if (isLoading.get()) {
            return CompletableFuture.completedFuture(
                LoadResult.error(IllegalStateException("Document is already loading"))
            )
        }

        return CompletableFuture.supplyAsync {
            try {
                isLoading.set(true)

                // Validate configuration
                configuration.validate()
                currentConfiguration = configuration

                val source = configuration.documentSource!!

                notifyEngineListeners { it.onDocumentLoadStart(source) }
                progressCallback?.invoke(0.1f)

                // Close existing document
                closeDocument()
                progressCallback?.invoke(0.2f)

                // Create new document
                val document = PdfDocument(source, configuration.password)
                progressCallback?.invoke(0.3f)

                // Open the document
                document.open()
                currentDocument = document
                progressCallback?.invoke(0.7f)

                // Configure based on settings
                applyDocumentConfiguration(document, configuration)
                progressCallback?.invoke(0.9f)

                // Get document metadata
                val metadata = document.getDocumentMetadata()
                val pageCount = document.getPageCount()

                // Preload page information if requested
                if (configuration.prerenderPages > 0) {
                    document.preloadPageInfo(0 until minOf(configuration.prerenderPages, pageCount))
                }

                progressCallback?.invoke(1.0f)
                notifyEngineListeners { it.onDocumentLoadComplete(pageCount, metadata) }

                Log.d(TAG, "Document loaded successfully: $pageCount pages")
                LoadResult.success(pageCount, metadata)

            } catch (e: Exception) {
                val wrappedException = when (e) {
                    is InvalidSourceException, is SecurityException -> e
                    else -> InvalidSourceException(
                        configuration.documentSource?.getDescription() ?: "unknown",
                        "Failed to load document: ${e.message}",
                        e
                    )
                }

                notifyEngineListeners { it.onDocumentLoadError(wrappedException) }
                Log.e(TAG, "Failed to load document", wrappedException)
                LoadResult.error(wrappedException)

            } finally {
                isLoading.set(false)
            }
        }
    }

    /**
     * Renders a specific page
     * @param pageNumber the page number (0-indexed)
     * @param targetWidth target width in pixels
     * @param targetHeight target height in pixels
     * @param quality render quality
     * @param useCache whether to use cached results
     * @return future containing the rendered bitmap
     */
    fun renderPage(
        pageNumber: Int,
        targetWidth: Int,
        targetHeight: Int,
        quality: RenderQuality = RenderQuality.MEDIUM,
        useCache: Boolean = true
    ): Future<RenderPageResult> {

        val document = currentDocument ?: return CompletableFuture.completedFuture(
            RenderPageResult.error(IllegalStateException("No document loaded"))
        )

        val config = currentConfiguration ?: return CompletableFuture.completedFuture(
            RenderPageResult.error(IllegalStateException("No configuration available"))
        )

        // Check if page is cached
        if (useCache) {
            val zoomLevel = targetWidth.toFloat() / document.getPageInfo(pageNumber).originalWidth
            val cachedBitmap = cacheManager.getCachedPage(pageNumber, zoomLevel)
            if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                val cachedPageInfo = cacheManager.getCachedPageInfo(pageNumber, zoomLevel)!!
                return CompletableFuture.completedFuture(
                    RenderPageResult.success(cachedBitmap, cachedPageInfo, fromCache = true)
                )
            }
        }

        // Create render task
        val taskId = "render_${pageNumber}_${renderRequestCounter.incrementAndGet()}"
        val renderTask = RenderTask(pageRenderer, taskId)
            .configureFullPageRender(
                pdfPage = document.getPage(pageNumber),
                pageNumber = pageNumber,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                quality = quality,
                backgroundColor = config.backgroundColor,
                nightMode = config.nightMode,
                antialiasing = config.antialiasingEnabled,
                renderAnnotations = config.annotationRenderingEnabled
            )
            .onProgress { progress ->
                // Could notify listeners about render progress
            }

        notifyEngineListeners { it.onPageRenderStart(pageNumber) }

        return renderQueue.submitTask(renderTask).thenApply { result ->
            when (result) {
                is RenderResult.Success -> {
                    // Cache the result
                    if (useCache) {
                        val zoomLevel = targetWidth.toFloat() / result.pageInfo.originalWidth
                        cacheManager.cachePage(pageNumber, zoomLevel, result.bitmap, result.pageInfo)
                    }

                    notifyEngineListeners { it.onPageRenderComplete(pageNumber, result.renderTimeMs) }
                    RenderPageResult.success(result.bitmap, result.pageInfo, fromCache = false)
                }

                is RenderResult.Error -> {
                    val pageError = PageRenderException(pageNumber, "Render failed", result.exception)
                    notifyEngineListeners { it.onPageRenderError(pageNumber, pageError) }
                    RenderPageResult.error(pageError)
                }

                is RenderResult.Cancelled -> {
                    RenderPageResult.error(IllegalStateException("Render task was cancelled"))
                }
            }
        }
    }

    /**
     * Renders a portion of a page (useful for zoomed views)
     * @param pageNumber the page number (0-indexed)
     * @param sourceRect the portion of the page to render (0.0-1.0 coordinates)
     * @param targetWidth target width in pixels
     * @param targetHeight target height in pixels
     * @param quality render quality
     * @return future containing the rendered bitmap
     */
    fun renderPagePortion(
        pageNumber: Int,
        sourceRect: RectF,
        targetWidth: Int,
        targetHeight: Int,
        quality: RenderQuality = RenderQuality.HIGH
    ): Future<RenderPageResult> {

        val document = currentDocument ?: return CompletableFuture.completedFuture(
            RenderPageResult.error(IllegalStateException("No document loaded"))
        )

        val config = currentConfiguration ?: return CompletableFuture.completedFuture(
            RenderPageResult.error(IllegalStateException("No configuration available"))
        )

        val taskId = "render_portion_${pageNumber}_${renderRequestCounter.incrementAndGet()}"
        val renderTask = RenderTask(pageRenderer, taskId)
            .configurePartialRender(
                pdfPage = document.getPage(pageNumber),
                pageNumber = pageNumber,
                sourceRect = sourceRect,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                quality = quality,
                backgroundColor = config.backgroundColor,
                nightMode = config.nightMode,
                antialiasing = config.antialiasingEnabled
            )

        return renderQueue.submitTask(renderTask).thenApply { result ->
            when (result) {
                is RenderResult.Success -> {
                    RenderPageResult.success(result.bitmap, result.pageInfo, fromCache = false)
                }
                is RenderResult.Error -> {
                    RenderPageResult.error(PageRenderException(pageNumber, "Portion render failed", result.exception))
                }
                is RenderResult.Cancelled -> {
                    RenderPageResult.error(IllegalStateException("Render task was cancelled"))
                }
            }
        }
    }

    /**
     * Renders a thumbnail of a page
     * @param pageNumber the page number (0-indexed)
     * @param maxSize maximum dimension size
     * @return future containing the thumbnail bitmap
     */
    fun renderThumbnail(
        pageNumber: Int,
        maxSize: Int = 200
    ): Future<RenderPageResult> {

        val document = currentDocument ?: return CompletableFuture.completedFuture(
            RenderPageResult.error(IllegalStateException("No document loaded"))
        )

        val config = currentConfiguration ?: return CompletableFuture.completedFuture(
            RenderPageResult.error(IllegalStateException("No configuration available"))
        )

        val taskId = "thumbnail_${pageNumber}_${renderRequestCounter.incrementAndGet()}"
        val renderTask = RenderTask(pageRenderer, taskId)
            .configureThumbnailRender(
                pdfPage = document.getPage(pageNumber),
                pageNumber = pageNumber,
                maxSize = maxSize,
                backgroundColor = config.backgroundColor
            )

        return renderQueue.submitTask(renderTask).thenApply { result ->
            when (result) {
                is RenderResult.Success -> {
                    RenderPageResult.success(result.bitmap, result.pageInfo, fromCache = false)
                }
                is RenderResult.Error -> {
                    RenderPageResult.error(
                        PageRenderException(
                            pageNumber,
                            "Thumbnail render failed",
                            result.exception
                        )
                    )
                }
                is RenderResult.Cancelled -> {
                    RenderPageResult.error(IllegalStateException("Render task was cancelled"))
                }
            }
        }
    }

    /**
     * Cancels all pending render tasks for a specific page
     * @param pageNumber the page number
     * @return number of tasks cancelled
     */
    fun cancelPageRender(pageNumber: Int): Int {
        return renderQueue.cancelTasksForPage(pageNumber)
    }

    /**
     * Cancels all pending render tasks
     * @return number of tasks cancelled
     */
    fun cancelAllRenders(): Int {
        return renderQueue.cancelAllTasks()
    }

    /**
     * Gets the current document
     * @return current PDF document or null if none loaded
     */
    fun getCurrentDocument(): PdfDocument? = currentDocument

    /**
     * Gets the current configuration
     * @return current configuration or null if none set
     */
    fun getCurrentConfiguration(): Configuration? = currentConfiguration

    /**
     * Gets page information for a specific page
     * @param pageNumber the page number (0-indexed)
     * @return page information
     */
    fun getPageInfo(pageNumber: Int): PageInfo? {
        return currentDocument?.getPageInfo(pageNumber)
    }

    /**
     * Gets information for all pages
     * @return list of all page information
     */
    fun getAllPageInfo(): List<PageInfo> {
        return currentDocument?.getAllPageInfo() ?: emptyList()
    }

    /**
     * Gets the total number of pages in the current document
     * @return page count or 0 if no document loaded
     */
    fun getPageCount(): Int {
        return currentDocument?.getPageCount() ?: 0
    }

    /**
     * Gets document metadata
     * @return document metadata or null if no document loaded
     */
    fun getDocumentMetadata(): PdfDocument.DocumentMetadata? {
        return currentDocument?.getDocumentMetadata()
    }

    /**
     * Optimizes memory usage by cleaning up resources
     */
    fun optimizeMemoryUsage() {
        memoryManager.performMemoryCleanup()
        currentDocument?.optimizeMemoryUsage()
        cacheManager.trimToSize(0.7f) // Trim cache to 70%
        bitmapPool.trimToSize(bitmapPool.getMaxSizeBytes() * 3 / 4) // Trim pool to 75%

        Log.d(TAG, "Memory optimization completed")
    }

    /**
     * Enables or disables low memory mode
     * @param enabled whether to enable low memory mode
     */
    fun setLowMemoryMode(enabled: Boolean) {
        memoryManager.setLowMemoryMode(enabled)

        if (enabled) {
            // Aggressive settings for low memory mode
            currentDocument?.setMaxConcurrentPages(1)
            cacheManager.trimToSize(0.3f)
            bitmapPool.trimToSize(bitmapPool.getMaxSizeBytes() / 3)
        } else {
            // Restore normal settings
            currentDocument?.setMaxConcurrentPages(DEFAULT_MAX_CONCURRENT_PAGES)
        }

        Log.d(TAG, "Low memory mode: ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Gets comprehensive engine statistics
     * @return engine statistics
     */
    fun getEngineStatistics(): EngineStatistics {
        val documentStats = currentDocument?.getDocumentStatistics()
        val cacheStats = cacheManager.getStatistics()
        val queueStats = renderQueue.getStatistics()
        val memoryInfo = memoryManager.getMemoryInfo()
        val bitmapPoolStats = bitmapPool.getStatistics()

        return EngineStatistics(
            isInitialized = isInitialized.get(),
            isDocumentLoaded = currentDocument != null,
            isLoading = isLoading.get(),
            totalRenderRequests = renderRequestCounter.get(),
            documentStatistics = documentStats,
            cacheStatistics = cacheStats,
            queueStatistics = queueStats,
            memoryInfo = memoryInfo,
            bitmapPoolStatistics = bitmapPoolStats
        )
    }

    /**
     * Preloads pages around a specific page for smooth scrolling
     * @param centerPage the center page to preload around
     * @param range number of pages to preload in each direction
     * @param quality render quality for preloading
     */
    fun preloadPagesAround(
        centerPage: Int,
        range: Int = 2,
        quality: RenderQuality = RenderQuality.LOW
    ) {
        val document = currentDocument ?: return
        val pageCount = document.getPageCount()

        val startPage = (centerPage - range).coerceAtLeast(0)
        val endPage = (centerPage + range).coerceAtMost(pageCount - 1)

        for (pageNumber in startPage..endPage) {
            if (pageNumber != centerPage) {
                val pageInfo = document.getPageInfo(pageNumber)
                renderPage(
                    pageNumber = pageNumber,
                    targetWidth = pageInfo.originalWidth.toInt(),
                    targetHeight = pageInfo.originalHeight.toInt(),
                    quality = quality,
                    useCache = true
                )
            }
        }

        Log.d(TAG, "Preloading pages $startPage-$endPage around page $centerPage")
    }

    /**
     * Clears render cache for pages outside the visible range
     * @param visiblePages list of currently visible page numbers
     * @param keepRange additional range to keep cached
     */
    fun clearOutOfRangeCache(visiblePages: List<Int>, keepRange: Int = 2) {
        if (visiblePages.isEmpty()) return

        val minVisible = visiblePages.minOrNull()!! - keepRange
        val maxVisible = visiblePages.maxOrNull()!! + keepRange

        // Remove cached pages outside the range
        val cachedPages = cacheManager.getCachedPagesList()
        for (cachedPage in cachedPages) {
            if (cachedPage.pageNumber < minVisible || cachedPage.pageNumber > maxVisible) {
                cacheManager.removePage(cachedPage.pageNumber)
            }
        }

        Log.d(TAG, "Cleared cache outside range $minVisible-$maxVisible")
    }

    /**
     * Updates engine configuration
     * @param newConfiguration the new configuration
     */
    fun updateConfiguration(newConfiguration: Configuration) {
        currentConfiguration = newConfiguration

        // Apply memory-related settings
        if (newConfiguration.lowMemoryMode) {
            setLowMemoryMode(true)
        }

        // Update render queue settings
        if (newConfiguration.backgroundThreads != renderQueue.getStatistics().maximumPoolSize) {
            // Would need to recreate render queue with new thread count
            Log.d(TAG, "Configuration updated (thread count change requires restart)")
        }
    }

    /**
     * Adds an engine listener
     * @param id unique listener ID
     * @param listener the listener to add
     */
    fun addEngineListener(id: String, listener: EngineListener) {
        engineListeners[id] = listener
    }

    /**
     * Removes an engine listener
     * @param id the listener ID to remove
     */
    fun removeEngineListener(id: String) {
        engineListeners.remove(id)
    }

    /**
     * Closes the current document and releases resources
     */
    fun closeDocument() {
        currentDocument?.let { document ->
            cancelAllRenders()
            document.close()
            currentDocument = null
            cacheManager.clearCache()
            Log.d(TAG, "Document closed")
        }
    }

    /**
     * Shuts down the engine and releases all resources
     */
    fun shutdown() {
        if (!isInitialized.get()) return

        try {
            // Cancel all operations
            cancelAllRenders()

            // Close document
            closeDocument()

            // Shutdown components
            renderQueue.shutdown()
            cacheManager.clearCache()
            bitmapPool.clear()

            // Clear listeners
            engineListeners.clear()

            notifyEngineListeners { it.onEngineShutdown() }
            isInitialized.set(false)

            Log.d(TAG, "PDF Engine shut down successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error during engine shutdown", e)
        }
    }

    // Private helper methods

    private fun applyDocumentConfiguration(document: PdfDocument, config: Configuration) {
        // Set maximum concurrent pages based on memory settings
        val maxPages = if (config.lowMemoryMode) 1 else config.offscreenPageLimit + 1
        document.setMaxConcurrentPages(maxPages)

        // Configure memory manager
        memoryManager.setLowMemoryMode(config.lowMemoryMode)
    }

    private fun handleRenderTaskCompleted(taskId: String, result: RenderResult) {
        when (result) {
            is RenderResult.Success -> {
                Log.d(TAG, "Render task completed: $taskId (${result.renderTimeMs}ms)")
            }
            is RenderResult.Error -> {
                Log.e(TAG, "Render task failed: $taskId", result.exception)
            }
            is RenderResult.Cancelled -> {
                Log.d(TAG, "Render task cancelled: $taskId")
            }
        }
    }

    private fun handleCriticalMemory() {
        Log.w(TAG, "Critical memory warning - performing aggressive cleanup")

        // Cancel non-essential render tasks
        val cancelledTasks = cancelAllRenders()

        // Aggressive memory cleanup
        cacheManager.trimToSize(0.2f) // Keep only 20% of cache
        bitmapPool.trimToSize(bitmapPool.getMaxSizeBytes() / 4) // Keep only 25% of pool
        currentDocument?.optimizeMemoryUsage()

        // Force garbage collection
        System.gc()

        Log.w(TAG, "Critical memory cleanup completed: cancelled $cancelledTasks tasks")
    }

    private fun handleOutOfMemory() {
        Log.e(TAG, "Out of memory error - performing emergency cleanup")

        // Emergency cleanup
        cancelAllRenders()
        cacheManager.clearCache()
        bitmapPool.clear()
        currentDocument?.optimizeMemoryUsage()

        // Enable low memory mode
        setLowMemoryMode(true)

        System.gc()
        Log.e(TAG, "Emergency memory cleanup completed")
    }

    private fun notifyEngineListeners(action: (EngineListener) -> Unit) {
        for (listener in engineListeners.values) {
            try {
                action(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying engine listener", e)
            }
        }
    }

    /**
     * Result of document loading operation
     */
    sealed class LoadResult {
        data class Success(
            val pageCount: Int,
            val metadata: PdfDocument.DocumentMetadata
        ) : LoadResult()

        data class Error(val exception: Exception) : LoadResult()

        companion object {
            fun success(pageCount: Int, metadata: PdfDocument.DocumentMetadata): Success {
                return Success(pageCount, metadata)
            }

            fun error(exception: Exception): Error {
                return Error(exception)
            }
        }
    }

    /**
     * Result of page rendering operation
     */
    sealed class RenderPageResult {
        data class Success(
            val bitmap: Bitmap,
            val pageInfo: PageInfo,
            val fromCache: Boolean
        ) : RenderPageResult()

        data class Error(val exception: Exception) : RenderPageResult()

        companion object {
            fun success(bitmap: Bitmap, pageInfo: PageInfo, fromCache: Boolean): Success {
                return Success(bitmap, pageInfo, fromCache)
            }

            fun error(exception: Exception): Error {
                return Error(exception)
            }
        }
    }

    /**
     * Comprehensive engine statistics
     */
    data class EngineStatistics(
        val isInitialized: Boolean,
        val isDocumentLoaded: Boolean,
        val isLoading: Boolean,
        val totalRenderRequests: Int,
        val documentStatistics: PdfDocument.DocumentStatistics?,
        val cacheStatistics: CacheManager.CacheStatistics,
        val queueStatistics: RenderQueue.QueueStatistics,
        val memoryInfo: MemoryManager.MemoryInfo,
        val bitmapPoolStatistics: BitmapPool.PoolStatistics
    ) {
        override fun toString(): String {
            return """
                PDF Engine Statistics:
                - Initialized: $isInitialized
                - Document Loaded: $isDocumentLoaded
                - Currently Loading: $isLoading
                - Total Render Requests: $totalRenderRequests
                - Document: ${documentStatistics ?: "No document"}
                - Cache: $cacheStatistics
                - Queue: $queueStatistics  
                - Memory: $memoryInfo
                - Bitmap Pool: $bitmapPoolStatistics
            """.trimIndent()
        }

        /**
         * Gets a summary of the most important metrics
         */
        fun getSummary(): String {
            return "Engine: ${if (isDocumentLoaded) "Document loaded" else "No document"}, " +
                    "Cache: ${String.format("%.1f", cacheStatistics.getHitRate() * 100)}% hit rate, " +
                    "Memory: ${String.format("%.1f", memoryInfo.memoryUsageRatio * 100)}% used, " +
                    "Queue: ${queueStatistics.pendingTasksCount} pending"
        }
    }
}

// Extension function to make CompletableFuture available
private fun <T> CompletableFuture.Companion.completedFuture(value: T): java.util.concurrent.CompletableFuture<T> {
    return java.util.concurrent.CompletableFuture.completedFuture(value)
}

private fun <T> CompletableFuture.Companion.supplyAsync(supplier: () -> T): java.util.concurrent.CompletableFuture<T> {
    return java.util.concurrent.CompletableFuture.supplyAsync(supplier)
}