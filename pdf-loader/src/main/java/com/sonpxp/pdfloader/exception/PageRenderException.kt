package com.sonpxp.pdfloader.exception

/**
 * Exception thrown when page rendering fails
 */
class PageRenderException(
    val pageNumber: Int,
    message: String,
    cause: Throwable? = null
) : PDFViewException("Page $pageNumber render error: $message", cause) {

    override fun getCategory(): String = "RENDER_ERROR"

    override fun getUserMessage(): String = "Failed to render page ${pageNumber + 1}. The page may be corrupted."

    override fun isRecoverable(): Boolean = true // Might work with different zoom/quality
}
