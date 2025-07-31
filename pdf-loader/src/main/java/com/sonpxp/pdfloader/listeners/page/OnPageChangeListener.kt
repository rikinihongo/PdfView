package com.sonpxp.pdfloader.listeners.page

/**
 * Listener for page changes
 */
fun interface OnPageChangeListener {
    /**
     * Called when the current page changes
     * @param page the new current page number (0-indexed)
     * @param pageCount the total number of pages
     */
    fun onPageChanged(page: Int, pageCount: Int)
}