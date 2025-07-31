package com.sonpxp.pdfloader.listeners.interaction

import android.view.MotionEvent

/**
 * Listener for long press events
 */
fun interface OnLongPressListener {
    /**
     * Called when the user performs a long press
     * @param event the motion event for the long press
     * @return true if the event was handled, false otherwise
     */
    fun onLongPress(event: MotionEvent): Boolean
}