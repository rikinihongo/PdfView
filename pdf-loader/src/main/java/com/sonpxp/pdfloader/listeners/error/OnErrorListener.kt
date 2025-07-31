package com.sonpxp.pdfloader.listeners.error

/**
 * Listener for general PDF viewer errors
 */
fun interface OnErrorListener {
    /**
     * Called when a general error occurs in the PDF viewer
     * @param error the throwable that caused the error
     */
    fun onError(error: Throwable)
}
