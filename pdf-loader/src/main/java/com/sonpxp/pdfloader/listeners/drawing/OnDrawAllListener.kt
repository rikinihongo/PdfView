package com.sonpxp.pdfloader.listeners.drawing

import android.graphics.Canvas

/**
 * Listener for drawing on all visible pages separately
 */
fun interface OnDrawAllListener {
    /**
     * Called when drawing should be performed on each visible page
     * This is called once for each visible page, allowing different drawing per page
     *
     * @param canvas the canvas to draw on
     * @param pageWidth the width of the page in pixels
     * @param pageHeight the height of the page in pixels
     * @param displayedPage the page number being drawn (0-indexed)
     */
    fun onLayerDrawn(canvas: Canvas, pageWidth: Float, pageHeight: Float, displayedPage: Int)
}