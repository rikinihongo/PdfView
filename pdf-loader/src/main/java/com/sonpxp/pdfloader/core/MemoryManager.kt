package com.sonpxp.pdfloader.core

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.sonpxp.pdfloader.exception.MemoryException
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages memory usage and optimization for the PDF viewer
 * Monitors memory consumption and provides cleanup strategies
 */
class MemoryManager(
    private val context: Context,
    private val cacheManager: CacheManager,
    private val bitmapPool: BitmapPool
) {

    companion object {
        private const val TAG = "MemoryManager"
        private const val LOW_MEMORY_THRESHOLD = 0.85f // 85% of max memory
        private const val CRITICAL_MEMORY_THRESHOLD = 0.95f // 95% of max memory
        private const val MEMORY_CHECK_INTERVAL = 5000L // 5 seconds
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()
    private val memoryListeners = ConcurrentLinkedQueue<WeakReference<MemoryListener>>()
    private var isLowMemoryMode = false
    private var lastMemoryCheck = 0L
    private var maxHeapSize: Long = 0
    private var memoryWarningCount = 0

    /**
     * Interface for listening to memory events
     */
    interface MemoryListener {
        fun onLowMemoryWarning(availableMemory: Long, totalMemory: Long) {}
        fun onCriticalMemoryWarning(availableMemory: Long, totalMemory: Long) {}
        fun onMemoryRecovered(availableMemory: Long, totalMemory: Long) {}
        fun onOutOfMemoryError(requiredMemory: Long, availableMemory: Long) {}
    }

    init {
        maxHeapSize = Runtime.getRuntime().maxMemory()
        Log.d(TAG, "Initialized MemoryManager with max heap: ${formatBytes(maxHeapSize)}")
    }

    /**
     * Checks current memory status and takes action if needed
     * @param forceCheck whether to force a check even if recently checked
     * @return current memory status
     */
    fun checkMemoryStatus(forceCheck: Boolean = false): MemoryStatus {
        val currentTime = System.currentTimeMillis()

        if (!forceCheck && currentTime - lastMemoryCheck < MEMORY_CHECK_INTERVAL) {
            return getCurrentMemoryStatus()
        }

        lastMemoryCheck = currentTime

        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val availableMemory = maxHeapSize - usedMemory
        val memoryUsageRatio = usedMemory.toFloat() / maxHeapSize.toFloat()

        // Get system memory info
        activityManager.getMemoryInfo(memoryInfo)
        val systemAvailableMemory = memoryInfo.availMem
        val systemTotalMemory = memoryInfo.totalMem
        val systemLowMemory = memoryInfo.lowMemory

        val status = when {
            memoryUsageRatio >= CRITICAL_MEMORY_THRESHOLD || systemLowMemory -> {
                handleCriticalMemory(availableMemory, totalMemory)
                MemoryStatus.CRITICAL
            }
            memoryUsageRatio >= LOW_MEMORY_THRESHOLD -> {
                handleLowMemory(availableMemory, totalMemory)
                MemoryStatus.LOW
            }
            else -> {
                if (isLowMemoryMode) {
                    handleMemoryRecovered(availableMemory, totalMemory)
                }
                MemoryStatus.NORMAL
            }
        }

        Log.d(TAG, "Memory status: $status, Used: ${formatBytes(usedMemory)}, " +
                "Available: ${formatBytes(availableMemory)}, " +
                "Usage: ${String.format("%.1f", memoryUsageRatio * 100)}%")

        return status
    }

    /**
     * Performs aggressive memory cleanup
     * @param requiredMemory optional amount of memory needed
     * @return amount of memory freed
     */
    fun performMemoryCleanup(requiredMemory: Long = 0): Long {
        Log.d(TAG, "Performing memory cleanup, required: ${formatBytes(requiredMemory)}")

        val initialMemory = getUsedMemory()
        var cleanupSteps = 0

        // Step 1: Trim cache to 50%
        cacheManager.trimToSize(0.5f)
        cleanupSteps++

        val afterCacheTrim = getUsedMemory()
        if (requiredMemory > 0 && initialMemory - afterCacheTrim >= requiredMemory) {
            Log.d(TAG, "Cleanup complete after step $cleanupSteps")
            return initialMemory - afterCacheTrim
        }

        // Step 2: Trim bitmap pool
        bitmapPool.trimToSize(bitmapPool.getMaxSizeBytes() / 2)
        cleanupSteps++

        val afterPoolTrim = getUsedMemory()
        if (requiredMemory > 0 && initialMemory - afterPoolTrim >= requiredMemory) {
            Log.d(TAG, "Cleanup complete after step $cleanupSteps")
            return initialMemory - afterPoolTrim
        }

        // Step 3: Clear entire cache
        cacheManager.clearCache()
        cleanupSteps++

        val afterCacheClear = getUsedMemory()
        if (requiredMemory > 0 && initialMemory - afterCacheClear >= requiredMemory) {
            Log.d(TAG, "Cleanup complete after step $cleanupSteps")
            return initialMemory - afterCacheClear
        }

        // Step 4: Clear bitmap pool
        bitmapPool.clear()
        cleanupSteps++

        // Step 5: Force garbage collection
        System.gc()
        Thread.yield() // Give GC a chance to run
        cleanupSteps++

        val finalMemory = getUsedMemory()
        val freedMemory = initialMemory - finalMemory

        Log.d(TAG, "Memory cleanup completed in $cleanupSteps steps, freed: ${formatBytes(freedMemory)}")
        return freedMemory
    }

    /**
     * Allocates memory safely with fallback strategies
     * @param sizeBytes required memory size
     * @param operation description of the operation for logging
     * @return true if allocation is likely to succeed
     * @throws MemoryException if allocation is not possible
     */
    @Throws(MemoryException::class)
    fun allocateMemorySafely(sizeBytes: Long, operation: String = "unknown"): Boolean {
        val availableMemory = getAvailableMemory()

        if (sizeBytes <= availableMemory) {
            return true // Should be safe to allocate
        }

        Log.w(TAG, "Insufficient memory for $operation: need ${formatBytes(sizeBytes)}, " +
                "available ${formatBytes(availableMemory)}")

        // Try cleanup
        val freedMemory = performMemoryCleanup(sizeBytes)
        val newAvailableMemory = getAvailableMemory()

        if (sizeBytes <= newAvailableMemory) {
            Log.d(TAG, "Memory allocation possible after cleanup for $operation")
            return true
        }

        // Still not enough memory
        notifyMemoryListeners { listener ->
            listener.onOutOfMemoryError(sizeBytes, newAvailableMemory)
        }

        throw MemoryException(
            requiredMemory = sizeBytes,
            availableMemory = newAvailableMemory,
            message = "Cannot allocate ${formatBytes(sizeBytes)} for $operation. " +
                    "Only ${formatBytes(newAvailableMemory)} available after cleanup."
        )
    }

    /**
     * Enables or disables low memory mode
     * @param enabled whether to enable low memory mode
     */
    fun setLowMemoryMode(enabled: Boolean) {
        if (isLowMemoryMode != enabled) {
            isLowMemoryMode = enabled
            Log.d(TAG, "Low memory mode: ${if (enabled) "enabled" else "disabled"}")

            if (enabled) {
                // Aggressive cleanup when entering low memory mode
                cacheManager.trimToSize(0.3f)
                bitmapPool.trimToSize(bitmapPool.getMaxSizeBytes() / 3)
            }
        }
    }

    /**
     * Gets whether low memory mode is active
     */
    fun isLowMemoryMode(): Boolean = isLowMemoryMode

    /**
     * Adds a memory listener
     */
    fun addMemoryListener(listener: MemoryListener) {
        // Clean up dead references first
        cleanupDeadReferences()
        memoryListeners.offer(WeakReference(listener))
    }

    /**
     * Removes a memory listener
     */
    fun removeMemoryListener(listener: MemoryListener) {
        val iterator = memoryListeners.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            val currentListener = ref.get()
            if (currentListener == null || currentListener === listener) {
                iterator.remove()
            }
        }
    }

    /**
     * Gets current memory usage information
     */
    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val availableMemory = maxHeapSize - usedMemory

        activityManager.getMemoryInfo(memoryInfo)

        return MemoryInfo(
            usedHeapMemory = usedMemory,
            totalHeapMemory = totalMemory,
            maxHeapMemory = maxHeapSize,
            availableHeapMemory = availableMemory,
            systemAvailableMemory = memoryInfo.availMem,
            systemTotalMemory = memoryInfo.totalMem,
            systemLowMemory = memoryInfo.lowMemory,
            memoryUsageRatio = usedMemory.toFloat() / maxHeapSize.toFloat(),
            cacheMemoryUsage = cacheManager.getStatistics().currentSize,
            bitmapPoolUsage = bitmapPool.getCurrentSizeBytes(),
            isLowMemoryMode = isLowMemoryMode,
            memoryWarningCount = memoryWarningCount
        )
    }

    /**
     * Gets memory usage recommendations
     */
    fun getMemoryRecommendations(): List<MemoryRecommendation> {
        val memInfo = getMemoryInfo()
        val recommendations = mutableListOf<MemoryRecommendation>()

        if (memInfo.memoryUsageRatio > 0.8f) {
            recommendations.add(
                MemoryRecommendation(
                    type = RecommendationType.REDUCE_CACHE_SIZE,
                    priority = Priority.HIGH,
                    description = "Memory usage is high (${String.format("%.1f", memInfo.memoryUsageRatio * 100)}%). Consider reducing cache size.",
                    action = "Reduce cache size to free up memory"
                )
            )
        }

        if (memInfo.cacheMemoryUsage > memInfo.maxHeapMemory * 0.3f) {
            recommendations.add(
                MemoryRecommendation(
                    type = RecommendationType.TRIM_CACHE,
                    priority = Priority.MEDIUM,
                    description = "Cache is using ${formatBytes(memInfo.cacheMemoryUsage)} of memory.",
                    action = "Trim cache to reduce memory usage"
                )
            )
        }

        if (memInfo.systemLowMemory) {
            recommendations.add(
                MemoryRecommendation(
                    type = RecommendationType.ENABLE_LOW_MEMORY_MODE,
                    priority = Priority.HIGH,
                    description = "System is running low on memory.",
                    action = "Enable low memory mode for better performance"
                )
            )
        }

        if (memInfo.bitmapPoolUsage > memInfo.maxHeapMemory * 0.2f) {
            recommendations.add(
                MemoryRecommendation(
                    type = RecommendationType.REDUCE_BITMAP_POOL,
                    priority = Priority.LOW,
                    description = "Bitmap pool is using ${formatBytes(memInfo.bitmapPoolUsage)} of memory.",
                    action = "Consider reducing bitmap pool size"
                )
            )
        }

        return recommendations
    }

    private fun getCurrentMemoryStatus(): MemoryStatus {
        val usedMemory = getUsedMemory()
        val memoryUsageRatio = usedMemory.toFloat() / maxHeapSize.toFloat()

        return when {
            memoryUsageRatio >= CRITICAL_MEMORY_THRESHOLD -> MemoryStatus.CRITICAL
            memoryUsageRatio >= LOW_MEMORY_THRESHOLD -> MemoryStatus.LOW
            else -> MemoryStatus.NORMAL
        }
    }

    private fun handleLowMemory(availableMemory: Long, totalMemory: Long) {
        if (!isLowMemoryMode) {
            memoryWarningCount++
            setLowMemoryMode(true)

            // Trim cache to 70%
            cacheManager.trimToSize(0.7f)

            notifyMemoryListeners { listener ->
                listener.onLowMemoryWarning(availableMemory, totalMemory)
            }
        }
    }

    private fun handleCriticalMemory(availableMemory: Long, totalMemory: Long) {
        memoryWarningCount++
        setLowMemoryMode(true)

        // Aggressive cleanup
        performMemoryCleanup()

        notifyMemoryListeners { listener ->
            listener.onCriticalMemoryWarning(availableMemory, totalMemory)
        }
    }

    private fun handleMemoryRecovered(availableMemory: Long, totalMemory: Long) {
        setLowMemoryMode(false)

        notifyMemoryListeners { listener ->
            listener.onMemoryRecovered(availableMemory, totalMemory)
        }
    }

    private fun notifyMemoryListeners(action: (MemoryListener) -> Unit) {
        cleanupDeadReferences()

        for (ref in memoryListeners) {
            ref.get()?.let { listener ->
                try {
                    action(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying memory listener", e)
                }
            }
        }
    }

    private fun cleanupDeadReferences() {
        val iterator = memoryListeners.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            if (ref.get() == null) {
                iterator.remove()
            }
        }
    }

    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun getAvailableMemory(): Long {
        return maxHeapSize - getUsedMemory()
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
     * Memory status levels
     */
    enum class MemoryStatus {
        NORMAL,
        LOW,
        CRITICAL
    }

    /**
     * Memory usage information
     */
    data class MemoryInfo(
        val usedHeapMemory: Long,
        val totalHeapMemory: Long,
        val maxHeapMemory: Long,
        val availableHeapMemory: Long,
        val systemAvailableMemory: Long,
        val systemTotalMemory: Long,
        val systemLowMemory: Boolean,
        val memoryUsageRatio: Float,
        val cacheMemoryUsage: Long,
        val bitmapPoolUsage: Long,
        val isLowMemoryMode: Boolean,
        val memoryWarningCount: Int
    ) {
        override fun toString(): String {
            return "Memory Info: " +
                    "Used: ${formatBytes(usedHeapMemory)}/${formatBytes(maxHeapMemory)} " +
                    "(${String.format("%.1f", memoryUsageRatio * 100)}%), " +
                    "Cache: ${formatBytes(cacheMemoryUsage)}, " +
                    "Pool: ${formatBytes(bitmapPoolUsage)}, " +
                    "System Low: $systemLowMemory, " +
                    "Low Memory Mode: $isLowMemoryMode"
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

    /**
     * Memory optimization recommendation
     */
    data class MemoryRecommendation(
        val type: RecommendationType,
        val priority: Priority,
        val description: String,
        val action: String
    )

    enum class RecommendationType {
        REDUCE_CACHE_SIZE,
        TRIM_CACHE,
        ENABLE_LOW_MEMORY_MODE,
        REDUCE_BITMAP_POOL,
        CLEAR_UNUSED_PAGES,
        REDUCE_RENDER_QUALITY
    }

    enum class Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}