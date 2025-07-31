package com.sonpxp.pdfloader.listeners.interaction

/**
 * Listener for PDF internal link taps
 */
fun interface OnLinkTapListener {
    /**
     * Called when a PDF internal link is tapped
     * @param uri the URI of the link that was tapped
     * @return true if the link was handled, false to use default handling
     */
    fun onLinkTap(uri: String): Boolean
}