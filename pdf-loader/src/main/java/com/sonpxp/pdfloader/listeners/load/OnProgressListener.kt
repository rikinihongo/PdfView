package com.sonpxp.pdfloader.listeners.load

/**
 * Listener for loading progress updates
 */
fun interface OnProgressListener {
    /**
     * Called during document loading to report progress
     * @param progress loading progress from 0.0 to 1.0
     */
    fun onProgress(progress: Float)
}