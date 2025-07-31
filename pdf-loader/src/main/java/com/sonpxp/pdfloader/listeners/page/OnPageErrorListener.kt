package com.sonpxp.pdfloader.listeners.page

/**
 * Listener for page-specific errors
 */
fun interface OnPageErrorListener {
    /**
     * Called when an error occurs while loading or rendering a specific page
     * @param page the page number that caused the error (0-indexed)
     * @param error the throwable that caused the error
     */
    fun onPageError(page: Int, error: Throwable)
}