package com.sonpxp.pdfloader.listeners.page

import com.sonpxp.pdfloader.PageScrollState

/**
 * Listener for page scrolling events
 */
interface OnPageScrollListener {
    /**
     * Called when a page is being scrolled
     * @param page the current page number (0-indexed)
     * @param positionOffset the offset of the current page as a percentage (0.0 to 1.0)
     */
    fun onPageScrolled(page: Int, positionOffset: Float)

    /**
     * Called when the page scroll state changes
     * @param state the new scroll state
     */
    fun onPageScrollStateChanged(state: PageScrollState)
}