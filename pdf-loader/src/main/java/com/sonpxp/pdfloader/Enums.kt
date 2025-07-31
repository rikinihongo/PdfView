package com.sonpxp.pdfloader


/**
 * Policy for fitting pages within the view
 */
enum class FitPolicy {
    /** Fit page to view width */
    WIDTH,

    /** Fit page to view height */
    HEIGHT,

    /** Fit both width and height (may cause distortion) */
    BOTH,

    /** Automatically detect the best fit policy */
    AUTO
}

/**
 * Scroll direction for PDF pages
 */
enum class ScrollDirection {
    /** Horizontal scrolling only */
    HORIZONTAL,

    /** Vertical scrolling only */
    VERTICAL,

    /** Both horizontal and vertical scrolling */
    BOTH
}

/**
 * Animation types for page transitions
 */
enum class AnimationType {
    /** Slide transition */
    SLIDE,

    /** Fade transition */
    FADE,

    /** Scale transition */
    SCALE,

    /** Flip transition */
    FLIP,

    /** No animation */
    NONE
}

/**
 * Rendering quality levels
 */
enum class RenderQuality {
    /** Low quality - 72 DPI */
    LOW(72),

    /** Medium quality - 150 DPI */
    MEDIUM(150),

    /** High quality - 300 DPI */
    HIGH(300),

    /** Ultra quality - 600 DPI */
    ULTRA(600);

    val dpi: Int

    constructor(dpi: Int) {
        this.dpi = dpi
    }

    /**
     * Gets the scale factor relative to LOW quality
     */
    fun getScaleFactor(): Float {
        return dpi.toFloat() / LOW.dpi.toFloat()
    }
}

/**
 * Minimap position options
 */
enum class MinimapPosition {
    /** Top left corner */
    TOP_LEFT,

    /** Top right corner */
    TOP_RIGHT,

    /** Bottom left corner */
    BOTTOM_LEFT,

    /** Bottom right corner */
    BOTTOM_RIGHT,

    /** Center left edge */
    CENTER_LEFT,

    /** Center right edge */
    CENTER_RIGHT
}

/**
 * Page number overlay position options
 */
enum class PageNumberPosition {
    /** Top left corner */
    TOP_LEFT,

    /** Top center */
    TOP_CENTER,

    /** Top right corner */
    TOP_RIGHT,

    /** Bottom left corner */
    BOTTOM_LEFT,

    /** Bottom center */
    BOTTOM_CENTER,

    /** Bottom right corner */
    BOTTOM_RIGHT
}

/**
 * Page scroll states (similar to ViewPager)
 */
enum class PageScrollState {
    /** The pager is idle, not scrolling */
    IDLE,

    /** The pager is being dragged by the user */
    DRAGGING,

    /** The pager is settling to a final position */
    SETTLING
}

/**
 * Zoom animation states
 */
enum class ZoomState {
    /** No zoom animation active */
    IDLE,

    /** Zoom animation in progress */
    ANIMATING,

    /** User is actively zooming (pinch gesture) */
    USER_ZOOM
}

/**
 * Loading states for the PDF document
 */
enum class LoadState {
    /** Not started loading */
    NOT_LOADED,

    /** Currently loading */
    LOADING,

    /** Successfully loaded */
    LOADED,

    /** Error occurred during loading */
    ERROR
}

/**
 * Page rendering states
 */
enum class PageRenderState {
    /** Page not yet rendered */
    NOT_RENDERED,

    /** Page is being rendered */
    RENDERING,

    /** Page successfully rendered */
    RENDERED,

    /** Error occurred during rendering */
    ERROR
}

/**
 * Text selection modes
 */
enum class TextSelectionMode {
    /** Text selection disabled */
    DISABLED,

    /** Basic text selection enabled */
    ENABLED,

    /** Advanced text selection with word boundaries */
    ADVANCED
}

/**
 * Security levels for PDF documents
 */
enum class SecurityLevel {
    /** No security restrictions */
    NONE,

    /** Basic security (password required) */
    BASIC,

    /** Advanced security (additional restrictions) */
    ADVANCED
}

enum class DocumentLoadState {
    NOT_LOADED,
    LOADED,
    ERROR,
    LOADING
}

enum class RenderTaskState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}