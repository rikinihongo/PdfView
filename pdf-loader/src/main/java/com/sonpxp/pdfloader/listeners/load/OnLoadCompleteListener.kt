package com.sonpxp.pdfloader.listeners.load

/**
 * Listener for PDF document loading completion
 */
fun interface OnLoadCompleteListener {
    /**
     * Called when the PDF document has been successfully loaded
     * @param totalPages the total number of pages in the document
     */
    fun onLoadComplete(totalPages: Int)
}