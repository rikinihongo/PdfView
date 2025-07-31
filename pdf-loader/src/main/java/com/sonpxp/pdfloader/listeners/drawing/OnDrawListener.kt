package com.sonpxp.pdfloader.listeners.drawing

import android.graphics.Canvas

/**
 * Listener for drawing on the current visible page
 */
fun interface OnDrawListener {
    /**
     * Called when drawing should be performed on the current visible page
     * This is called for overlay drawing on the currently displayed page
     *
     * @param canvas the canvas to draw on
     * @param pageWidth the width of the page in pixels
     * @param pageHeight the height of the page in pixels
     * @param displayedPage the current displayed page number (0-indexed)
     */
    fun onLayerDrawn(canvas: Canvas, pageWidth: Float, pageHeight: Float, displayedPage: Int)
}
