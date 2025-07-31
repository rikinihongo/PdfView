package com.sonpxp.pdfviewer

enum class FitPolicy {
    WIDTH,    // fit to width
    HEIGHT,   // fit to height
    BOTH,     // fit both dimensions
    AUTO      // auto detect best fit
}

enum class ScrollDirection {
    HORIZONTAL,
    VERTICAL,
    BOTH
}

enum class AnimationType {
    SLIDE,
    FADE,
    SCALE,
    FLIP,
    NONE
}

enum class RenderQuality {
    LOW,      // 72 DPI
    MEDIUM,   // 150 DPI
    HIGH,     // 300 DPI
    ULTRA     // 600 DPI
}

enum class MinimapPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER_LEFT,
    CENTER_RIGHT
}

enum class PageNumberPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
}