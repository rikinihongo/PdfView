package com.sonpxp.pdfloader.listeners.interaction

import android.view.MotionEvent

/**
 * Listener for single tap events
 */
fun interface OnTapListener {
    /**
     * Called when the user performs a single tap
     * @param event the motion event for the tap
     * @return true if the event was handled, false otherwise
     */
    fun onTap(event: MotionEvent): Boolean
}