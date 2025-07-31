package com.sonpxp.pdfloader.gestures


import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import com.sonpxp.pdfloader.listeners.interaction.OnDoubleTapListener
import com.sonpxp.pdfloader.listeners.interaction.OnLongPressListener
import com.sonpxp.pdfloader.listeners.interaction.OnTapListener
import com.sonpxp.pdfloader.listeners.interaction.OnZoomListener
import com.sonpxp.pdfloader.model.Configuration

/**
 * Main gesture coordinator that manages all touch interactions
 * Orchestrates different gesture detectors and handlers
 */
class GestureController(
    private val context: Context,
    private val configuration: Configuration
) {

    // Gesture detectors
    private val tapDetector = TapDetector(context, configuration)
    private val pinchDetector = PinchDetector(context, configuration)
    private val dragDetector = DragDetector(context, configuration)

    // Gesture handlers
    private val zoomHandler = ZoomHandler(configuration)
    private val scrollHandler = ScrollHandler(configuration)
    private val flingHandler = FlingHandler(context, configuration)

    // State tracking
    private var isGestureInProgress = false
    private var activeGestureType = GestureType.NONE
    private var velocityTracker: VelocityTracker? = null

    // Listeners
    private var onTapListener: OnTapListener? = null
    private var onDoubleTapListener: OnDoubleTapListener? = null
    private var onLongPressListener: OnLongPressListener? = null
    private var onZoomListener: OnZoomListener? = null

    // Internal gesture callbacks
    private var onScrollListener: ((Float, Float) -> Unit)? = null
    private var onFlingListener: ((Float, Float) -> Unit)? = null
    private var onGestureStateChangeListener: ((GestureType, GestureState) -> Unit)? = null

    enum class GestureType {
        NONE, TAP, DOUBLE_TAP, LONG_PRESS, SCROLL, ZOOM, FLING
    }

    enum class GestureState {
        STARTED, IN_PROGRESS, ENDED, CANCELLED
    }

    init {
        setupGestureDetectors()
    }

    /**
     * Main touch event processing
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = false

        // Add to velocity tracker
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handled = handleActionDown(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                handled = handlePointerDown(event)
            }
            MotionEvent.ACTION_MOVE -> {
                handled = handleActionMove(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                handled = handlePointerUp(event)
            }
            MotionEvent.ACTION_UP -> {
                handled = handleActionUp(event)
            }
            MotionEvent.ACTION_CANCEL -> {
                handled = handleActionCancel(event)
            }
        }

        return handled
    }

    private fun setupGestureDetectors() {
        // Setup tap detector callbacks
        tapDetector.setCallbacks(object : TapDetector.TapCallback {
            override fun onSingleTap(event: MotionEvent): Boolean {
                setActiveGesture(GestureType.TAP, GestureState.STARTED)
                val handled = onTapListener?.onTap(event) ?: false
                setActiveGesture(GestureType.TAP, GestureState.ENDED)
                return handled
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                setActiveGesture(GestureType.DOUBLE_TAP, GestureState.STARTED)
                val handled = onDoubleTapListener?.onDoubleTap(event) ?: false
                setActiveGesture(GestureType.DOUBLE_TAP, GestureState.ENDED)
                return handled
            }

            override fun onLongPress(event: MotionEvent): Boolean {
                setActiveGesture(GestureType.LONG_PRESS, GestureState.STARTED)
                val handled = onLongPressListener?.onLongPress(event) ?: false
                setActiveGesture(GestureType.LONG_PRESS, GestureState.ENDED)
                return handled
            }
        })

        // Setup pinch detector callbacks
        pinchDetector.setCallbacks(object : PinchDetector.PinchCallback {
            override fun onPinchStart(detector: PinchDetector): Boolean {
                setActiveGesture(GestureType.ZOOM, GestureState.STARTED)
                return true
            }

            override fun onPinch(detector: PinchDetector): Boolean {
                setActiveGesture(GestureType.ZOOM, GestureState.IN_PROGRESS)
                val result = zoomHandler.handlePinchZoom(
                    detector.getScaleFactor(),
                    detector.getFocusX(),
                    detector.getFocusY()
                )

                if (result.zoomChanged) {
                    onZoomListener?.onZoom(result.newZoom)
                }

                return true
            }

            override fun onPinchEnd(detector: PinchDetector) {
                setActiveGesture(GestureType.ZOOM, GestureState.ENDED)
            }
        })

        // Setup drag detector callbacks
        dragDetector.setCallbacks(object : DragDetector.DragCallback {
            override fun onDragStart(startX: Float, startY: Float): Boolean {
                if (activeGestureType != GestureType.ZOOM) {
                    setActiveGesture(GestureType.SCROLL, GestureState.STARTED)
                    return true
                }
                return false
            }

            override fun onDrag(deltaX: Float, deltaY: Float, totalX: Float, totalY: Float): Boolean {
                if (activeGestureType == GestureType.SCROLL) {
                    setActiveGesture(GestureType.SCROLL, GestureState.IN_PROGRESS)
                    val result = scrollHandler.handleScroll(deltaX, deltaY)
                    onScrollListener?.invoke(result.newScrollX, result.newScrollY)
                    return true
                }
                return false
            }

            override fun onDragEnd(velocityX: Float, velocityY: Float) {
                if (activeGestureType == GestureType.SCROLL) {
                    setActiveGesture(GestureType.SCROLL, GestureState.ENDED)

                    // Check if should start fling
                    if (shouldStartFling(velocityX, velocityY)) {
                        setActiveGesture(GestureType.FLING, GestureState.STARTED)
                        flingHandler.startFling(velocityX, velocityY) { newX, newY ->
                            onScrollListener?.invoke(newX, newY)
                        }
                    }
                }
            }
        })
    }

    private fun handleActionDown(event: MotionEvent): Boolean {
        // Reset gesture state
        activeGestureType = GestureType.NONE
        isGestureInProgress = false

        // Stop any ongoing fling
        flingHandler.stopFling()

        // Forward to detectors
        var handled = false
        handled = tapDetector.onTouchEvent(event) || handled
        handled = dragDetector.onTouchEvent(event) || handled

        return handled
    }

    private fun handlePointerDown(event: MotionEvent): Boolean {
        // Multi-touch started - likely pinch zoom
        var handled = false
        handled = pinchDetector.onTouchEvent(event) || handled

        // Cancel other gestures when pinch starts
        if (handled) {
            tapDetector.cancel()
            dragDetector.cancel()
        }

        return handled
    }

    private fun handleActionMove(event: MotionEvent): Boolean {
        var handled = false

        // Forward to appropriate detectors based on pointer count
        if (event.pointerCount >= 2) {
            handled = pinchDetector.onTouchEvent(event) || handled
        } else {
            handled = tapDetector.onTouchEvent(event) || handled
            handled = dragDetector.onTouchEvent(event) || handled
        }

        return handled
    }

    private fun handlePointerUp(event: MotionEvent): Boolean {
        var handled = false
        handled = pinchDetector.onTouchEvent(event) || handled

        // If going back to single touch, resume other detectors
        if (event.pointerCount == 2) { // One pointer remaining
            handled = dragDetector.onTouchEvent(event) || handled
        }

        return handled
    }

    private fun handleActionUp(event: MotionEvent): Boolean {
        // Calculate final velocity
        velocityTracker?.let { tracker ->
            tracker.computeCurrentVelocity(1000) // pixels per second
            val velocityX = tracker.xVelocity
            val velocityY = tracker.yVelocity

            // Forward velocity to detectors
            dragDetector.setFinalVelocity(velocityX, velocityY)
        }

        var handled = false
        handled = tapDetector.onTouchEvent(event) || handled
        handled = dragDetector.onTouchEvent(event) || handled
        handled = pinchDetector.onTouchEvent(event) || handled

        // Cleanup
        cleanupGesture()

        return handled
    }

    private fun handleActionCancel(event: MotionEvent): Boolean {
        // Cancel all active gestures
        tapDetector.cancel()
        dragDetector.cancel()
        pinchDetector.cancel()
        flingHandler.stopFling()

        setActiveGesture(GestureType.NONE, GestureState.CANCELLED)
        cleanupGesture()

        return true
    }

    private fun shouldStartFling(velocityX: Float, velocityY: Float): Boolean {
        val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
        val velocity = Math.sqrt((velocityX * velocityX + velocityY * velocityY).toDouble()).toFloat()
        return velocity > minFlingVelocity && configuration.pageFling
    }

    private fun setActiveGesture(type: GestureType, state: GestureState) {
        activeGestureType = type
        isGestureInProgress = state == GestureState.IN_PROGRESS
        onGestureStateChangeListener?.invoke(type, state)
    }

    private fun cleanupGesture() {
        velocityTracker?.recycle()
        velocityTracker = null
        isGestureInProgress = false
        activeGestureType = GestureType.NONE
    }

    // Public API for setting listeners
    fun setOnTapListener(listener: OnTapListener?) {
        onTapListener = listener
    }

    fun setOnDoubleTapListener(listener: OnDoubleTapListener?) {
        onDoubleTapListener = listener
    }

    fun setOnLongPressListener(listener: OnLongPressListener?) {
        onLongPressListener = listener
    }

    fun setOnZoomListener(listener: OnZoomListener?) {
        onZoomListener = listener
    }

    fun setOnScrollListener(listener: (Float, Float) -> Unit) {
        onScrollListener = listener
    }

    fun setOnFlingListener(listener: (Float, Float) -> Unit) {
        onFlingListener = listener
    }

    fun setOnGestureStateChangeListener(listener: (GestureType, GestureState) -> Unit) {
        onGestureStateChangeListener = listener
    }

    // Gesture state queries
    fun isGestureActive(): Boolean = isGestureInProgress

    fun getActiveGestureType(): GestureType = activeGestureType

    fun getCurrentZoom(): Float = zoomHandler.getCurrentZoom()

    fun getCurrentScroll(): PointF = scrollHandler.getCurrentScroll()

    // Manual gesture control
    fun zoom(scale: Float, centerX: Float, centerY: Float) {
        val result = zoomHandler.setZoom(scale, centerX, centerY)
        if (result.zoomChanged) {
            onZoomListener?.onZoom(result.newZoom)
        }
    }

    fun scroll(x: Float, y: Float) {
        val result = scrollHandler.setScroll(x, y)
        onScrollListener?.invoke(result.newScrollX, result.newScrollY)
    }

    fun stopAllGestures() {
        tapDetector.cancel()
        dragDetector.cancel()
        pinchDetector.cancel()
        flingHandler.stopFling()
        cleanupGesture()
    }

    // Configuration updates
    fun updateConfiguration(newConfig: Configuration) {
        zoomHandler.updateConfiguration(newConfig)
        scrollHandler.updateConfiguration(newConfig)
        flingHandler.updateConfiguration(newConfig)
    }

    /**
     * Gets gesture statistics for debugging
     */
    fun getGestureStatistics(): GestureStatistics {
        return GestureStatistics(
            activeGesture = activeGestureType,
            isGestureInProgress = isGestureInProgress,
            currentZoom = zoomHandler.getCurrentZoom(),
            currentScroll = scrollHandler.getCurrentScroll(),
            tapCount = tapDetector.getTapCount(),
            isFlingActive = flingHandler.isFlingActive(),
            lastGestureTime = System.currentTimeMillis()
        )
    }

    data class GestureStatistics(
        val activeGesture: GestureType,
        val isGestureInProgress: Boolean,
        val currentZoom: Float,
        val currentScroll: PointF,
        val tapCount: Int,
        val isFlingActive: Boolean,
        val lastGestureTime: Long
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("Gesture Statistics:")
                appendLine("  Active: $activeGesture")
                appendLine("  In Progress: $isGestureInProgress")
                appendLine("  Zoom: ${String.format("%.2f", currentZoom)}")
                appendLine("  Scroll: (${currentScroll.x.toInt()}, ${currentScroll.y.toInt()})")
                appendLine("  Tap Count: $tapCount")
                appendLine("  Fling Active: $isFlingActive")
            }
        }
    }
}