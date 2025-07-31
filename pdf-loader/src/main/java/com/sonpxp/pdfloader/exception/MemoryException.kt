package com.sonpxp.pdfloader.exception

/**
 * Exception thrown when memory-related issues occur
 */
class MemoryException(
    val requiredMemory: Long = -1,
    val availableMemory: Long = -1,
    message: String,
    cause: Throwable? = null
) : PDFViewException("Memory error: $message", cause) {

    override fun getCategory(): String = "MEMORY_ERROR"

    override fun getUserMessage(): String = "Not enough memory to display the PDF. Try closing other apps or reducing the zoom level."

    override fun isRecoverable(): Boolean = true

    /**
     * Gets formatted memory information
     */
    fun getMemoryInfo(): String {
        return if (requiredMemory > 0 && availableMemory > 0) {
            "Required: ${formatBytes(requiredMemory)}, Available: ${formatBytes(availableMemory)}"
        } else {
            "Memory information not available"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}