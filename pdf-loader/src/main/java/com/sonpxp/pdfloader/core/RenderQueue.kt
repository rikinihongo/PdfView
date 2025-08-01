package com.sonpxp.pdfloader.core

import android.graphics.HardwareBufferRenderer
import android.util.Log
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Manages a queue of rendering tasks with priority scheduling
 * Provides efficient task scheduling and resource management
 */
class RenderQueue(
    private val corePoolSize: Int = 2,
    private val maximumPoolSize: Int = 4,
    private val keepAliveTime: Long = 60L,
    private val queueCapacity: Int = 50,
) {

    companion object {
        private const val TAG = "RenderQueue"
    }

    private val taskIdCounter = AtomicLong(0)
    private val activeTaskCount = AtomicInteger(0)

    // Custom thread pool with priority queue
    private val executor: ThreadPoolExecutor
    private val pendingTasks = ConcurrentHashMap<String, RenderTask>()
    private val completedTasks = ConcurrentHashMap<String, HardwareBufferRenderer.RenderResult>()
    private val queueListeners = ConcurrentLinkedQueue<QueueListener>()

    // Statistics
    private var totalTasksSubmitted = 0L
    private var totalTasksCompleted = 0L
    private var totalTasksCancelled = 0L
    private var totalRenderTime = 0L

    init {
        // Create priority-based blocking queue
        val priorityQueue = PriorityBlockingQueue<Runnable>(
            queueCapacity,
            Comparator { r1, r2 ->
                val task1 = (r1 as? PriorityFutureTask)?.renderTask
                val task2 = (r2 as? PriorityFutureTask)?.renderTask

                when {
                    task1 == null && task2 == null -> 0
                    task1 == null -> 1
                    task2 == null -> -1
                    else -> RenderTask.Priority.compare(task1.getPriority(), task2.getPriority())
                }
            }
        )

        executor = ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            priorityQueue,
            RenderThreadFactory(),
            RejectedExecutionHandler { task, executor ->
                Log.w(TAG, "Task rejected by executor, queue is full")
                notifyQueueListeners { it.onTaskRejected() }
            }
        )

        // Allow core threads to timeout
        executor.allowCoreThreadTimeOut(true)

        Log.d(TAG, "RenderQueue initialized with $corePoolSize-$maximumPoolSize threads")
    }

    /**
     * Interface for listening to queue events
     */
    interface QueueListener {
        fun onTaskSubmitted(taskId: String, queueSize: Int) {}
        fun onTaskStarted(taskId: String, activeCount: Int) {}
        fun onTaskCompleted(taskId: String, result: RenderResult) {}
        fun onTaskCancelled(taskId: String) {}
        fun onTaskRejected() {}
        fun onQueueEmpty() {}
    }

    /**
     * Submits a render task to the queue
     * @param task the render task to execute
     * @return future that can be used to cancel or get result
     */
    fun submitTask(task: RenderTask): Future<RenderResult> {
        val taskId = task.getTaskId()

        // Check if task is already pending or completed
        if (pendingTasks.containsKey(taskId)) {
            Log.d(TAG, "Task already pending: $taskId")
            return CompletableFuture.completedFuture(
                RenderResult.error(
                    taskId,
                    IllegalStateException("Task already pending")
                )
            )
        }

        completedTasks[taskId]?.let { cachedResult ->
            Log.d(TAG, "Returning cached result for task: $taskId")
            return CompletableFuture.completedFuture(cachedResult) as Future<RenderResult>
        }

        // Add to pending tasks
        pendingTasks[taskId] = task
        totalTasksSubmitted++

        // Create priority wrapper
        val priorityTask = PriorityFutureTask(task) { result ->
            handleTaskCompletion(task, result)
        }

        // Submit to executor
        val future = executor.submit(priorityTask)

        notifyQueueListeners {
            it.onTaskSubmitted(taskId, executor.queue.size)
        }

        Log.d(TAG, "Submitted task: $taskId (queue size: ${executor.queue.size})")
        return future
    }

    /**
     * Submits a high priority task that jumps to front of queue
     * @param task the urgent render task
     * @return future for the task result
     */
    fun submitUrgentTask(task: RenderTask): Future<RenderResult> {
        // Create task with URGENT priority
        val urgentTask = RenderTask(task.pageRenderer, task.getTaskId(), RenderTask.Priority.URGENT)

        // Copy configuration from original task
        when (task.renderType) {
            RenderTask.RenderType.FULL_PAGE -> {
                urgentTask.configureFullPageRender(/* copy params */)
            }
            // Handle other render types...
        }

        return submitTask(urgentTask)
    }

    /**
     * Cancels a specific task
     * @param taskId the ID of the task to cancel
     * @return true if task was cancelled, false if not found or already completed
     */
    fun cancelTask(taskId: String): Boolean {
        val task = pendingTasks[taskId]
        if (task != null) {
            task.cancel()
            pendingTasks.remove(taskId)
            totalTasksCancelled++

            notifyQueueListeners { it.onTaskCancelled(taskId) }
            Log.d(TAG, "Cancelled task: $taskId")
            return true
        }
        return false
    }

    /**
     * Cancels all pending tasks for a specific page
     * @param pageNumber the page number
     * @return number of tasks cancelled
     */
    fun cancelTasksForPage(pageNumber: Int): Int {
        var cancelledCount = 0
        val tasksToCancel = pendingTasks.values.filter { it.getPageNumber() == pageNumber }

        for (task in tasksToCancel) {
            if (cancelTask(task.getTaskId())) {
                cancelledCount++
            }
        }

        Log.d(TAG, "Cancelled $cancelledCount tasks for page $pageNumber")
        return cancelledCount
    }

    /**
     * Cancels all pending tasks
     * @return number of tasks cancelled
     */
    fun cancelAllTasks(): Int {
        val cancelledCount = pendingTasks.size

        pendingTasks.values.forEach { task ->
            task.cancel()
        }

        pendingTasks.clear()
        executor.purge() // Remove cancelled tasks from queue
        totalTasksCancelled += cancelledCount

        Log.d(TAG, "Cancelled all $cancelledCount pending tasks")
        return cancelledCount
    }

    /**
     * Pauses the queue (stops processing new tasks)
     */
    fun pause() {
        executor.pause()
        Log.d(TAG, "RenderQueue paused")
    }

    /**
     * Resumes the queue
     */
    fun resume() {
        executor.resume()
        Log.d(TAG, "RenderQueue resumed")
    }

    /**
     * Shuts down the queue gracefully
     */
    fun shutdown() {
        cancelAllTasks()
        executor.shutdown()

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }

        Log.d(TAG, "RenderQueue shut down")
    }

    /**
     * Gets queue statistics
     */
    fun getStatistics(): QueueStatistics {
        return QueueStatistics(
            totalTasksSubmitted = totalTasksSubmitted,
            totalTasksCompleted = totalTasksCompleted,
            totalTasksCancelled = totalTasksCancelled,
            pendingTasksCount = pendingTasks.size,
            activeTasksCount = activeTaskCount.get(),
            queueSize = executor.queue.size,
            corePoolSize = executor.corePoolSize,
            maximumPoolSize = executor.maximumPoolSize,
            currentPoolSize = executor.poolSize,
            averageRenderTime = if (totalTasksCompleted > 0) totalRenderTime / totalTasksCompleted else 0L,
            completedTasksCacheSize = completedTasks.size
        )
    }

    /**
     * Gets information about pending tasks
     */
    fun getPendingTasks(): List<RenderTask.TaskInfo> {
        return pendingTasks.values.map { it.getTaskInfo() }
            .sortedWith(compareByDescending<RenderTask.TaskInfo> { it.priority.value }
                .thenBy { it.pageNumber })
    }

    /**
     * Clears completed task cache
     */
    fun clearCompletedTaskCache() {
        val clearedCount = completedTasks.size
        completedTasks.clear()
        Log.d(TAG, "Cleared $clearedCount completed tasks from cache")
    }

    /**
     * Adds a queue listener
     */
    fun addQueueListener(listener: QueueListener) {
        queueListeners.offer(listener)
    }

    /**
     * Removes a queue listener
     */
    fun removeQueueListener(listener: QueueListener) {
        queueListeners.remove(listener)
    }

    private fun handleTaskCompletion(task: RenderTask, result: RenderResult) {
        val taskId = task.getTaskId()

        // Remove from pending
        pendingTasks.remove(taskId)
        activeTaskCount.decrementAndGet()

        // Add to completed cache
        completedTasks[taskId] = result
        totalTasksCompleted++
        totalRenderTime += result.renderTimeMs

        // Limit completed cache size
        if (completedTasks.size > 100) {
            val oldestTask = completedTasks.keys.first()
            completedTasks.remove(oldestTask)
        }

        notifyQueueListeners {
            it.onTaskCompleted(taskId, result)
            if (pendingTasks.isEmpty() && executor.queue.isEmpty()) {
                it.onQueueEmpty()
            }
        }

        Log.d(TAG, "Task completed: $taskId (${result.renderTimeMs}ms)")
    }

    private fun notifyQueueListeners(action: (QueueListener) -> Unit) {
        for (listener in queueListeners) {
            try {
                action(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying queue listener", e)
            }
        }
    }

    /**
     * Custom thread factory for render threads
     */
    private class RenderThreadFactory : ThreadFactory {
        private val threadNumber = AtomicInteger(1)
        private val namePrefix = "PDFRender-"

        override fun newThread(r: Runnable): Thread {
            val thread = Thread(r, namePrefix + threadNumber.getAndIncrement())
            thread.isDaemon = false
            thread.priority = Thread.NORM_PRIORITY
            return thread
        }
    }

    /**
     * Priority wrapper for FutureTask
     */
    private class PriorityFutureTask(
        val renderTask: RenderTask,
        private val completionCallback: (RenderResult) -> Unit,
    ) : Callable<RenderResult> {

        override fun call(): RenderResult {
            val result = renderTask.call()
            completionCallback(result)
            return result
        }
    }

    /**
     * Queue statistics data class
     */
    data class QueueStatistics(
        val totalTasksSubmitted: Long,
        val totalTasksCompleted: Long,
        val totalTasksCancelled: Long,
        val pendingTasksCount: Int,
        val activeTasksCount: Int,
        val queueSize: Int,
        val corePoolSize: Int,
        val maximumPoolSize: Int,
        val currentPoolSize: Int,
        val averageRenderTime: Long,
        val completedTasksCacheSize: Int,
    ) {

        fun getCompletionRate(): Float {
            return if (totalTasksSubmitted > 0) {
                totalTasksCompleted.toFloat() / totalTasksSubmitted.toFloat()
            } else 0f
        }

        fun getCancellationRate(): Float {
            return if (totalTasksSubmitted > 0) {
                totalTasksCancelled.toFloat() / totalTasksSubmitted.toFloat()
            } else 0f
        }

        override fun toString(): String {
            return "RenderQueue Stats: ${totalTasksCompleted}/${totalTasksSubmitted} completed " +
                    "(${String.format("%.1f", getCompletionRate() * 100)}%), " +
                    "$pendingTasksCount pending, $activeTasksCount active, " +
                    "avg render time: ${averageRenderTime}ms"
        }
    }
}

/**
 * Extension to ThreadPoolExecutor to support pause/resume
 */
private class PausableThreadPoolExecutor(
    corePoolSize: Int,
    maximumPoolSize: Int,
    keepAliveTime: Long,
    unit: TimeUnit,
    workQueue: BlockingQueue<Runnable>,
    threadFactory: ThreadFactory,
    handler: RejectedExecutionHandler,
) : ThreadPoolExecutor(
    corePoolSize,
    maximumPoolSize,
    keepAliveTime,
    unit,
    workQueue,
    threadFactory,
    handler
) {

    private var isPaused = false
    private val pauseLock = ReentrantLock()
    private val unpaused = pauseLock.newCondition()

    override fun beforeExecute(t: Thread?, r: Runnable?) {
        super.beforeExecute(t, r)
        pauseLock.lock()
        try {
            while (isPaused) {
                unpaused.await()
            }
        } catch (ie: InterruptedException) {
            t?.interrupt()
        } finally {
            pauseLock.unlock()
        }
    }

    fun pause() {
        pauseLock.lock()
        try {
            isPaused = true
        } finally {
            pauseLock.unlock()
        }
    }

    fun resume() {
        pauseLock.lock()
        try {
            isPaused = false
            unpaused.signalAll()
        } finally {
            pauseLock.unlock()
        }
    }
}