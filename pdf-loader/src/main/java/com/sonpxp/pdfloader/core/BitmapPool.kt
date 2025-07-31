package com.sonpxp.pdfloader.core


import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Bitmap pool for reusing bitmap objects to reduce garbage collection
 * Organizes bitmaps by size for efficient reuse
 */
class BitmapPool(
    private val maxSizeBytes: Long = 50 * 1024 * 1024, // 50MB default
    private val maxBitmapCount: Int = 100
) {

    private val pools = ConcurrentHashMap<String, ConcurrentLinkedQueue<Bitmap>>()
    private var currentSizeBytes = 0L
    private var totalBitmapCount = 0

    companion object {
        private const val TAG = "BitmapPool"

        /**
         * Generates a key for bitmap dimensions and config
         */
        private fun generateKey(width: Int, height: Int, config: Bitmap.Config): String {
            return "${width}x${height}_${config.name}"
        }

        /**
         * Calculates the size of a bitmap in bytes
         */
        private fun calculateBitmapSize(bitmap: Bitmap): Long {
            return (bitmap.allocationByteCount).toLong()
        }

        /**
         * Calculates the size of a bitmap based on dimensions and config
         */
        private fun calculateBitmapSize(width: Int, height: Int, config: Bitmap.Config): Long {
            val bytesPerPixel = when (config) {
                Bitmap.Config.ALPHA_8 -> 1
                Bitmap.Config.RGB_565 -> 2
                Bitmap.Config.ARGB_4444 -> 2
                Bitmap.Config.ARGB_8888 -> 4
                Bitmap.Config.RGBA_F16 -> 8
                else -> 4 // Default to ARGB_8888
            }
            return (width * height * bytesPerPixel).toLong()
        }
    }

    /**
     * Gets a bitmap from the pool or creates a new one
     * @param width desired width
     * @param height desired height
     * @param config desired bitmap config
     * @return reused or new bitmap
     */
    @Synchronized
    fun getBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val key = generateKey(width, height, config)
        val pool = pools[key]

        // Try to get from pool
        pool?.poll()?.let { bitmap ->
            if (!bitmap.isRecycled && bitmap.isMutable) {
                totalBitmapCount--
                currentSizeBytes -= calculateBitmapSize(bitmap)
                bitmap.eraseColor(0) // Clear the bitmap
                Log.d(TAG, "Reused bitmap: ${width}x${height}, pool size: ${getTotalSize()}")
                return bitmap
            }
        }

        // Create new bitmap if pool is empty or bitmap is invalid
        return try {
            val newBitmap = Bitmap.createBitmap(width, height, config)
            Log.d(TAG, "Created new bitmap: ${width}x${height}, pool size: ${getTotalSize()}")
            newBitmap
        } catch (e: OutOfMemoryError) {
            // Try to free some memory and retry
            evictOldest(calculateBitmapSize(width, height, config))
            try {
                Bitmap.createBitmap(width, height, config)
            } catch (e2: OutOfMemoryError) {
                // If still failing, clear the entire pool and try once more
                clear()
                System.gc()
                Bitmap.createBitmap(width, height, config)
            }
        }
    }

    /**
     * Returns a bitmap to the pool for reuse
     * @param bitmap the bitmap to return
     * @return true if the bitmap was added to the pool, false otherwise
     */
    @Synchronized
    fun returnBitmap(bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.isRecycled || !bitmap.isMutable) {
            return false
        }

        val bitmapSize = calculateBitmapSize(bitmap)

        // Check if adding this bitmap would exceed limits
        if (totalBitmapCount >= maxBitmapCount || currentSizeBytes + bitmapSize > maxSizeBytes) {
            evictOldest(bitmapSize)
        }

        // Double-check limits after eviction
        if (totalBitmapCount >= maxBitmapCount || currentSizeBytes + bitmapSize > maxSizeBytes) {
            return false
        }

        val key = generateKey(bitmap.width, bitmap.height, bitmap.config!!)
        val pool = pools.getOrPut(key) { ConcurrentLinkedQueue() }

        pool.offer(bitmap)
        totalBitmapCount++
        currentSizeBytes += bitmapSize

        Log.d(TAG, "Returned bitmap to pool: ${bitmap.width}x${bitmap.height}, pool size: ${getTotalSize()}")
        return true
    }

    /**
     * Evicts the oldest bitmaps to make room for new ones
     * @param requiredSpace the amount of space needed in bytes
     */
    private fun evictOldest(requiredSpace: Long) {
        var freedSpace = 0L
        val poolsToRemove = mutableListOf<String>()

        for ((key, pool) in pools) {
            while (pool.isNotEmpty() && freedSpace < requiredSpace) {
                pool.poll()?.let { bitmap ->
                    if (!bitmap.isRecycled) {
                        val size = calculateBitmapSize(bitmap)
                        freedSpace += size
                        currentSizeBytes -= size
                        totalBitmapCount--
                        bitmap.recycle()
                    }
                }
            }

            if (pool.isEmpty()) {
                poolsToRemove.add(key)
            }

            if (freedSpace >= requiredSpace) {
                break
            }
        }

        // Remove empty pools
        poolsToRemove.forEach { pools.remove(it) }

        Log.d(TAG, "Evicted ${freedSpace} bytes from pool")
    }

    /**
     * Trims the pool to a specific size
     * @param maxSize maximum size in bytes
     */
    @Synchronized
    fun trimToSize(maxSize: Long) {
        while (currentSizeBytes > maxSize && totalBitmapCount > 0) {
            evictOldest(currentSizeBytes - maxSize)
        }
    }

    /**
     * Clears all bitmaps from the pool
     */
    @Synchronized
    fun clear() {
        for (pool in pools.values) {
            while (pool.isNotEmpty()) {
                pool.poll()?.let { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }
        pools.clear()
        currentSizeBytes = 0L
        totalBitmapCount = 0
        Log.d(TAG, "Cleared bitmap pool")
    }

    /**
     * Gets the current size of the pool in bytes
     */
    fun getCurrentSizeBytes(): Long = currentSizeBytes

    /**
     * Gets the maximum size of the pool in bytes
     */
    fun getMaxSizeBytes(): Long = maxSizeBytes

    /**
     * Gets the current number of bitmaps in the pool
     */
    fun getCurrentBitmapCount(): Int = totalBitmapCount

    /**
     * Gets the maximum number of bitmaps allowed in the pool
     */
    fun getMaxBitmapCount(): Int = maxBitmapCount

    /**
     * Gets the memory usage as a percentage
     */
    fun getUsagePercentage(): Float {
        return (currentSizeBytes.toFloat() / maxSizeBytes.toFloat()) * 100f
    }

    /**
     * Gets a formatted string showing total pool size
     */
    fun getTotalSize(): String {
        return "${formatBytes(currentSizeBytes)} / ${formatBytes(maxSizeBytes)} (${totalBitmapCount} bitmaps)"
    }

    /**
     * Gets pool statistics
     */
    fun getStatistics(): PoolStatistics {
        return PoolStatistics(
            currentSizeBytes = currentSizeBytes,
            maxSizeBytes = maxSizeBytes,
            currentBitmapCount = totalBitmapCount,
            maxBitmapCount = maxBitmapCount,
            poolCount = pools.size,
            usagePercentage = getUsagePercentage()
        )
    }

    /**
     * Checks if the pool can accommodate a bitmap of the given size
     */
    fun canAccommodate(width: Int, height: Int, config: Bitmap.Config): Boolean {
        val requiredSize = calculateBitmapSize(width, height, config)
        return currentSizeBytes + requiredSize <= maxSizeBytes && totalBitmapCount < maxBitmapCount
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
     * Data class containing pool statistics
     */
    data class PoolStatistics(
        val currentSizeBytes: Long,
        val maxSizeBytes: Long,
        val currentBitmapCount: Int,
        val maxBitmapCount: Int,
        val poolCount: Int,
        val usagePercentage: Float
    ) {
        override fun toString(): String {
            return "BitmapPool Stats: ${currentBitmapCount}/${maxBitmapCount} bitmaps, " +
                    "${formatBytes(currentSizeBytes)}/${formatBytes(maxSizeBytes)} " +
                    "(${String.format("%.1f", usagePercentage)}%), " +
                    "${poolCount} different sizes"
        }

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "${bytes}B"
                bytes < 1024 * 1024 -> "${bytes / 1024}KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
                else -> "${bytes / (1024 * 1024 * 1024)}GB"
            }
        }
    }
}