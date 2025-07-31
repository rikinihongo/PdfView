package com.sonpxp.pdfloader.listeners.interaction

import android.view.MotionEvent

/**
 * Listener for double tap events
 */
fun interface OnDoubleTapListener {
    /**
     * Called when the user performs a double tap
     * @param event the motion event for the double tap
     * @return true if the event was handled, false otherwise
     */
    fun onDoubleTap(event: MotionEvent): Boolean
}