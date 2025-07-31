package com.sonpxp.pdfloader.listeners.load

/**
 * Listener for initial rendering completion
 */
fun interface OnRenderListener {
    /**
     * Called when the document has been initially rendered and is ready for display
     * @param totalPages the total number of pages in the document
     * @param pageWidth the width of the first page in pixels
     * @param pageHeight the height of the first page in pixels
     */
    fun onInitiallyRendered(totalPages: Int, pageWidth: Float, pageHeight: Float)
}