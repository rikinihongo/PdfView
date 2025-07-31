package com.sonpxp.pdfloader.listeners.interaction

/**
 * Listener for zoom level changes
 */
fun interface OnZoomListener {
    /**
     * Called when the zoom level changes
     * @param zoom the new zoom level (1.0 = 100%)
     */
    fun onZoom(zoom: Float)
}