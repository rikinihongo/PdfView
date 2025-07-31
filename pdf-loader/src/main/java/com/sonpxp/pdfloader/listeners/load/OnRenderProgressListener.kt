package com.sonpxp.pdfloader.listeners.load

/**
 * Listener for individual page rendering progress
 */
fun interface OnRenderProgressListener {
    /**
     * Called during page rendering to report progress
     * @param page the page number being rendered (0-indexed)
     * @param progress rendering progress for this page from 0.0 to 1.0
     */
    fun onRenderProgress(page: Int, progress: Float)
}