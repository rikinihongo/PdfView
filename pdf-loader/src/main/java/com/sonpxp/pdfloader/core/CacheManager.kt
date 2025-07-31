package com.sonpxp.pdfloader.core


import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.sonpxp.pdfloader.model.PageInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages caching of rendered page bitmaps with LRU eviction policy
 * Provides efficient storage and retrieval of page bitmaps
 */
class CacheManager(
    maxCacheSizeMB: Int = 120,
    private val bitmapPool: BitmapPool = BitmapPool()
) {

    companion object {
        private const val TAG = "CacheManager"
        private const val BYTES_IN_MB = 1024 * 1024
    }

    private val maxCacheSize = maxCacheSizeMB * BYTES_IN_MB
    private val pageCache: LruCache<String, CachedPage>
    private val cacheStats = CacheStatistics()
    private val pageSizes = ConcurrentHashMap<String, Long>()

    init {
        pageCache = object : LruCache<String, CachedPage>(maxCacheSize) {
            override fun sizeOf(key: String, value: CachedPage): Int {
                return value.sizeInBytes.toInt()
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: CachedPage,
                newValue: CachedPage?
            ) {
                if (evicted) {
                    Log.d(TAG, "Evicted page from cache: $key (${formatBytes(oldValue.sizeInBytes)})")
                    cacheStats.recordEviction()

                    // Return bitmap to pool for reuse
                    bitmapPool.returnBitmap(oldValue.bitmap)
                }
                pageSizes.remove(key)
            }
        }
    }

    /**
     * Cached page data container
     */
    private data class CachedPage(
        val bitmap: Bitmap,
        val pageInfo: PageInfo,
        val cacheTime: Long = System.currentTimeMillis(),
        val lastAccessTime: Long = System.currentTimeMillis()
    ) {
        val sizeInBytes: Long = calculateBitmapSize(bitmap)

        fun updateAccessTime(): CachedPage {
            return copy(lastAccessTime = System.currentTimeMillis())
        }

        private fun calculateBitmapSize(bitmap: Bitmap): Long {
            return bitmap.allocationByteCount.toLong()
        }
    }

    /**
     * Caches a rendered page bitmap
     * @param pageNumber the page number (0-indexed)
     * @param zoomLevel the zoom level used for rendering
     * @param bitmap the rendered bitmap
     * @param pageInfo additional page information
     * @return true if cached successfully, false otherwise
     */
    fun cachePage(
        pageNumber: Int,
        zoomLevel: Float,
        bitmap: Bitmap,
        pageInfo: PageInfo
    ): Boolean {
        if (bitmap.isRecycled) {
            Log.w(TAG, "Attempted to cache recycled bitmap for page $pageNumber")
            return false
        }

        val key = generateCacheKey(pageNumber, zoomLevel)
        val cachedPage = CachedPage(bitmap, pageInfo)

        // Check if we have enough space
        val bitmapSize = cachedPage.sizeInBytes
        if (bitmapSize > maxCacheSize / 2) {
            Log.w(TAG, "Bitmap too large to cache: page $pageNumber, size: ${formatBytes(bitmapSize)}")
            return false
        }

        // Cache the page
        pageCache.put(key, cachedPage)
        pageSizes[key] = bitmapSize
        cacheStats.recordCacheAdd(bitmapSize)

        Log.d(TAG, "Cached page $pageNumber at zoom $zoomLevel (${formatBytes(bitmapSize)})")
        return true
    }

    /**
     * Retrieves a cached page bitmap
     * @param pageNumber the page number (0-indexed)
     * @param zoomLevel the zoom level
     * @return cached bitmap or null if not found
     */
    fun getCachedPage(pageNumber: Int, zoomLevel: Float): Bitmap? {
        val key = generateCacheKey(pageNumber, zoomLevel)
        val cachedPage = pageCache.get(key)

        return if (cachedPage != null && !cachedPage.bitmap.isRecycled) {
            // Update access time for LRU
            pageCache.put(key, cachedPage.updateAccessTime())
            cacheStats.recordCacheHit()
            Log.d(TAG, "Cache hit for page $pageNumber at zoom $zoomLevel")
            cachedPage.bitmap
        } else {
            cacheStats.recordCacheMiss()
            Log.d(TAG, "Cache miss for page $pageNumber at zoom $zoomLevel")
            null
        }
    }

    /**
     * Retrieves cached page info
     * @param pageNumber the page number (0-indexed)
     * @param zoomLevel the zoom level
     * @return cached page info or null if not found
     */
    fun getCachedPageInfo(pageNumber: Int, zoomLevel: Float): PageInfo? {
        val key = generateCacheKey(pageNumber, zoomLevel)
        return pageCache.get(key)?.pageInfo
    }

    /**
     * Checks if a page is cached at the specified zoom level
     * @param pageNumber the page number (0-indexed)
     * @param zoomLevel the zoom level
     * @return true if cached, false otherwise
     */
    fun isPageCached(pageNumber: Int, zoomLevel: Float): Boolean {
        val key = generateCacheKey(pageNumber, zoomLevel)
        val cachedPage = pageCache.get(key)
        return cachedPage != null && !cachedPage.bitmap.isRecycled
    }

    /**
     * Finds the best cached version of a page for a given zoom level
     * @param pageNumber the page number (0-indexed)
     * @param targetZoom the target zoom level
     * @param tolerance acceptable zoom difference
     * @return pair of (bitmap, actual zoom level) or null if not found
     */
    fun findBestCachedVersion(
        pageNumber: Int,
        targetZoom: Float,
        tolerance: Float = 0.5f
    ): Pair<Bitmap, Float>? {
        var bestMatch: Pair<Bitmap, Float>? = null
        var bestDifference = Float.MAX_VALUE

        // Check all possible zoom levels for this page
        val zoomLevels = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f)

        for (zoom in zoomLevels) {
            val key = generateCacheKey(pageNumber, zoom)
            val cachedPage = pageCache.get(key)

            if (cachedPage != null && !cachedPage.bitmap.isRecycled) {
                val difference = kotlin.math.abs(zoom - targetZoom)
                if (difference <= tolerance && difference < bestDifference) {
                    bestMatch = Pair(cachedPage.bitmap, zoom)
                    bestDifference = difference
                }
            }
        }

        return bestMatch
    }

    /**
     * Removes a specific page from cache
     * @param pageNumber the page number (0-indexed)
     * @param zoomLevel the zoom level (optional, removes all zoom levels if null)
     */
    fun removePage(pageNumber: Int, zoomLevel: Float? = null) {
        if (zoomLevel != null) {
            val key = generateCacheKey(pageNumber, zoomLevel)
            pageCache.remove(key)
            Log.d(TAG, "Removed page $pageNumber at zoom $zoomLevel from cache")
        } else {
            // Remove all zoom levels for this page
            val keysToRemove = mutableListOf<String>()
            val snapshot = pageCache.snapshot()

            for (key in snapshot.keys) {
                if (key.startsWith("${pageNumber}_")) {
                    keysToRemove.add(key)
                }
            }

            keysToRemove.forEach { pageCache.remove(it) }
            Log.d(TAG, "Removed all cached versions of page $pageNumber")
        }
    }

    /**
     * Removes pages outside the specified range
     * @param centerPage the center page to keep
     * @param range the range of pages to keep around the center
     */
    fun removeOutOfRangePages(centerPage: Int, range: Int) {
        val minPage = centerPage - range
        val maxPage = centerPage + range
        val keysToRemove = mutableListOf<String>()
        val snapshot = pageCache.snapshot()

        for (key in snapshot.keys) {
            val pageNumber = extractPageNumberFromKey(key)
            if (pageNumber < minPage || pageNumber > maxPage) {
                keysToRemove.add(key)
            }
        }

        keysToRemove.forEach { pageCache.remove(it) }

        if (keysToRemove.isNotEmpty()) {
            Log.d(TAG, "Removed ${keysToRemove.size} out-of-range pages from cache")
        }
    }

    /**
     * Clears all cached pages
     */
    fun clearCache() {
        val size = pageCache.size()
        pageCache.evictAll()
        pageSizes.clear()
        cacheStats.reset()
        Log.d(TAG, "Cleared entire cache ($size items)")
    }

    /**
     * Trims cache to specified percentage of maximum size
     * @param percentage target percentage (0.0 to 1.0)
     */
    fun trimToSize(percentage: Float) {
        val targetSize = (maxCacheSize * percentage).toInt()
        pageCache.trimToSize(targetSize)
        Log.d(TAG, "Trimmed cache to ${(percentage * 100).toInt()}% of maximum size")
    }

    /**
     * Gets current cache statistics
     */
    fun getStatistics(): CacheStatistics {
        cacheStats.updateCurrentStats(
            currentSize = pageCache.size().toLong(),
            maxSize = maxCacheSize.toLong(),
            itemCount = pageCache.snapshot().size
        )
        return cacheStats.copy()
    }

    /**
     * Gets a list of all cached pages with their info
     */
    fun getCachedPagesList(): List<CachedPageInfo> {
        val snapshot = pageCache.snapshot()
        return snapshot.entries.map { (key, cachedPage) ->
            val (pageNumber, zoomLevel) = parsePageKey(key)
            CachedPageInfo(
                pageNumber = pageNumber,
                zoomLevel = zoomLevel,
                sizeInBytes = cachedPage.sizeInBytes,
                cacheTime = cachedPage.cacheTime,
                lastAccessTime = cachedPage.lastAccessTime
            )
        }.sortedWith(compareByDescending<CachedPageInfo> { it.lastAccessTime }.thenBy { it.pageNumber })
    }

    private fun generateCacheKey(pageNumber: Int, zoomLevel: Float): String {
        return "${pageNumber}_${String.format("%.2f", zoomLevel)}"
    }

    private fun extractPageNumberFromKey(key: String): Int {
        return key.substringBefore("_").toIntOrNull() ?: -1
    }

    private fun parsePageKey(key: String): Pair<Int, Float> {
        val parts = key.split("_")
        val pageNumber = parts[0].toIntOrNull() ?: 0
        val zoomLevel = parts.getOrNull(1)?.toFloatOrNull() ?: 1.0f
        return Pair(pageNumber, zoomLevel)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }

    /**
     * Data class for cache statistics
     */
    data class CacheStatistics(
        var hits: Long = 0,
        var misses: Long = 0,
        var evictions: Long = 0,
        var adds: Long = 0,
        var totalBytesAdded: Long = 0,
        var currentSize: Long = 0,
        var maxSize: Long = 0,
        var itemCount: Int = 0
    ) {
        fun recordCacheHit() { hits++ }
        fun recordCacheMiss() { misses++ }
        fun recordEviction() { evictions++ }
        fun recordCacheAdd(bytes: Long) {
            adds++
            totalBytesAdded += bytes
        }

        fun updateCurrentStats(currentSize: Long, maxSize: Long, itemCount: Int) {
            this.currentSize = currentSize
            this.maxSize = maxSize
            this.itemCount = itemCount
        }

        fun getHitRate(): Float {
            val total = hits + misses
            return if (total > 0) hits.toFloat() / total.toFloat() else 0f
        }

        fun reset() {
            hits = 0
            misses = 0
            evictions = 0
            adds = 0
            totalBytesAdded = 0
        }

        fun copy(): CacheStatistics {
            return CacheStatistics(hits, misses, evictions, adds, totalBytesAdded, currentSize, maxSize, itemCount)
        }
    }

    /**
     * Information about a cached page
     */
    data class CachedPageInfo(
        val pageNumber: Int,
        val zoomLevel: Float,
        val sizeInBytes: Long,
        val cacheTime: Long,
        val lastAccessTime: Long
    )
}