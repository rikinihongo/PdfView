package com.sonpxp.pdfloader.layout

import com.sonpxp.pdfloader.utils.MathUtils
import android.graphics.PointF
import android.graphics.RectF
import com.sonpxp.pdfloader.ScrollDirection
import com.sonpxp.pdfloader.model.Configuration
import kotlin.math.*

/**
 * Handles page snapping functionality
 * Provides smooth scrolling to page boundaries and alignment
 */
class SnapHelper(
    private val configuration: Configuration,
    private val layoutManager: PageLayoutManager
) {

    /**
     * Snap target information
     */
    data class SnapTarget(
        val targetPosition: PointF,
        val targetPage: Int,
        val snapType: SnapType,
        val distance: Float,
        val duration: Long
    )

    /**
     * Types of snapping behavior
     */
    enum class SnapType {
        /** Snap to page center */
        CENTER,

        /** Snap to page top */
        TOP,

        /** Snap to page edges */
        EDGE,

        /** Snap to nearest page boundary */
        BOUNDARY,

        /** No snapping */
        NONE
    }

    /**
     * Snap configuration
     */
    data class SnapConfig(
        val enabled: Boolean = true,
        val snapType: SnapType = SnapType.BOUNDARY,
        val snapThreshold: Float = 0.5f, // 50% of page must be visible to snap to it
        val velocityThreshold: Float = 1000f, // Minimum velocity to trigger momentum snap
        val maxSnapDistance: Float = Float.MAX_VALUE,
        val snapDuration: Long = 300, // Animation duration in ms
        val easingFactor: Float = 0.25f // Easing strength (0 = linear, 1 = strong ease)
    )

    private val snapConfig = SnapConfig(
        enabled = configuration.pageSnap,
        snapType = when {
            configuration.swipeHorizontal -> SnapType.CENTER
            else -> SnapType.BOUNDARY
        }
    )

    /**
     * Calculates the best snap target for the current scroll position
     */
    fun calculateSnapTarget(
        currentScroll: PointF,
        velocity: PointF = PointF(0f, 0f)
    ): SnapTarget? {
        if (!snapConfig.enabled) {
            return null
        }

        val layoutResult = layoutManager.calculateLayout()
        if (layoutResult.pages.isEmpty()) {
            return null
        }

        return when (snapConfig.snapType) {
            SnapType.CENTER -> calculateCenterSnap(currentScroll, velocity, layoutResult)
            SnapType.TOP -> calculateTopSnap(currentScroll, velocity, layoutResult)
            SnapType.EDGE -> calculateEdgeSnap(currentScroll, velocity, layoutResult)
            SnapType.BOUNDARY -> calculateBoundarySnap(currentScroll, velocity, layoutResult)
            SnapType.NONE -> null
        }
    }

    /**
     * Calculates snap to page center
     */
    private fun calculateCenterSnap(
        currentScroll: PointF,
        velocity: PointF,
        layoutResult: PageLayoutManager.LayoutResult
    ): SnapTarget? {
        val targetPage = findTargetPageWithVelocity(currentScroll, velocity, layoutResult)
        val targetPageLayout = layoutResult.pages.find { it.pageNumber == targetPage } ?: return null

        val centerX = targetPageLayout.scaledBounds.centerX() - (layoutManager.getViewportWidth() / 2f)
        val centerY = targetPageLayout.scaledBounds.centerY() - (layoutManager.getViewportHeight() / 2f)

        val targetPosition = PointF(centerX, centerY)
        val distance = MathUtils.distance(currentScroll, targetPosition)
        val duration = calculateSnapDuration(distance, velocity)

        return SnapTarget(
            targetPosition = targetPosition,
            targetPage = targetPage,
            snapType = SnapType.CENTER,
            distance = distance,
            duration = duration
        )
    }

    /**
     * Calculates snap to page top
     */
    private fun calculateTopSnap(
        currentScroll: PointF,
        velocity: PointF,
        layoutResult: PageLayoutManager.LayoutResult
    ): SnapTarget? {
        val targetPage = findTargetPageWithVelocity(currentScroll, velocity, layoutResult)
        val targetPageLayout = layoutResult.pages.find { it.pageNumber == targetPage } ?: return null

        val centerX = targetPageLayout.scaledBounds.centerX() - (layoutManager.getViewportWidth() / 2f)
        val topY = targetPageLayout.scaledBounds.top

        val targetPosition = PointF(centerX, topY)
        val distance = MathUtils.distance(currentScroll, targetPosition)
        val duration = calculateSnapDuration(distance, velocity)

        return SnapTarget(
            targetPosition = targetPosition,
            targetPage = targetPage,
            snapType = SnapType.TOP,
            distance = distance,
            duration = duration
        )
    }

    /**
     * Calculates snap to page edges
     */
    private fun calculateEdgeSnap(
        currentScroll: PointF,
        velocity: PointF,
        layoutResult: PageLayoutManager.LayoutResult
    ): SnapTarget? {
        val visiblePages = layoutResult.pages.filter { it.isVisible }
        if (visiblePages.isEmpty()) return null

        var bestTarget: SnapTarget? = null
        var minDistance = Float.MAX_VALUE

        for (pageLayout in visiblePages) {
            // Check all four edges
            val edges = listOf(
                PointF(pageLayout.scaledBounds.left, currentScroll.y), // Left edge
                PointF(pageLayout.scaledBounds.right - layoutManager.getViewportWidth(), currentScroll.y), // Right edge
                PointF(currentScroll.x, pageLayout.scaledBounds.top), // Top edge
                PointF(currentScroll.x, pageLayout.scaledBounds.bottom - layoutManager.getViewportHeight()) // Bottom edge
            )

            for (edge in edges) {
                val distance = MathUtils.distance(currentScroll, edge)
                if (distance < minDistance && distance <= snapConfig.maxSnapDistance) {
                    minDistance = distance
                    bestTarget = SnapTarget(
                        targetPosition = edge,
                        targetPage = pageLayout.pageNumber,
                        snapType = SnapType.EDGE,
                        distance = distance,
                        duration = calculateSnapDuration(distance, velocity)
                    )
                }
            }
        }

        return bestTarget
    }

    /**
     * Calculates snap to page boundaries
     */
    private fun calculateBoundarySnap(
        currentScroll: PointF,
        velocity: PointF,
        layoutResult: PageLayoutManager.LayoutResult
    ): SnapTarget? {
        val targetPage = findTargetPageWithVelocity(currentScroll, velocity, layoutResult)
        val targetPageLayout = layoutResult.pages.find { it.pageNumber == targetPage } ?: return null

        // Determine best boundary based on current position and scroll direction
        val viewportRect = RectF(
            currentScroll.x,
            currentScroll.y,
            currentScroll.x + layoutManager.getViewportWidth(),
            currentScroll.y + layoutManager.getViewportHeight()
        )

        val pageRect = targetPageLayout.scaledBounds

        // Calculate different boundary options
        val boundaryOptions = mutableListOf<PointF>()

        when (configuration.pageScrollDirection) {
            ScrollDirection.VERTICAL -> {
                // Top and bottom boundaries
                boundaryOptions.add(PointF(
                    pageRect.centerX() - layoutManager.getViewportWidth() / 2f,
                    pageRect.top
                ))
                boundaryOptions.add(PointF(
                    pageRect.centerX() - layoutManager.getViewportWidth() / 2f,
                    pageRect.bottom - layoutManager.getViewportHeight()
                ))
            }
            ScrollDirection.HORIZONTAL -> {
                // Left and right boundaries
                boundaryOptions.add(PointF(
                    pageRect.left,
                    pageRect.centerY() - layoutManager.getViewportHeight() / 2f
                ))
                boundaryOptions.add(PointF(
                    pageRect.right - layoutManager.getViewportWidth(),
                    pageRect.centerY() - layoutManager.getViewportHeight() / 2f
                ))
            }
            ScrollDirection.BOTH -> {
                // All boundaries
                boundaryOptions.add(PointF(pageRect.left, pageRect.top))
                boundaryOptions.add(PointF(pageRect.right - layoutManager.getViewportWidth(), pageRect.top))
                boundaryOptions.add(PointF(pageRect.left, pageRect.bottom - layoutManager.getViewportHeight()))
                boundaryOptions.add(PointF(pageRect.right - layoutManager.getViewportWidth(), pageRect.bottom - layoutManager.getViewportHeight()))
            }
        }

        // Find the closest boundary
        val bestBoundary = boundaryOptions.minByOrNull {
            MathUtils.distance(currentScroll, it)
        } ?: return null

        val distance = MathUtils.distance(currentScroll, bestBoundary)
        val duration = calculateSnapDuration(distance, velocity)

        return SnapTarget(
            targetPosition = bestBoundary,
            targetPage = targetPage,
            snapType = SnapType.BOUNDARY,
            distance = distance,
            duration = duration
        )
    }

    /**
     * Finds the target page considering velocity for momentum-based snapping
     */
    private fun findTargetPageWithVelocity(
        currentScroll: PointF,
        velocity: PointF,
        layoutResult: PageLayoutManager.LayoutResult
    ): Int {
        val currentPage = layoutResult.currentPage
        val velocityMagnitude = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)

        // If velocity is high enough, consider momentum snapping
        if (velocityMagnitude > snapConfig.velocityThreshold) {
            val direction = when {
                abs(velocity.y) > abs(velocity.x) -> {
                    // Vertical movement
                    if (velocity.y > 0) 1 else -1
                }
                else -> {
                    // Horizontal movement
                    if (velocity.x > 0) 1 else -1
                }
            }

            val targetPage = currentPage + direction
            val validPages = layoutResult.pages.map { it.pageNumber }

            if (targetPage in validPages) {
                return targetPage
            }
        }

        // Default to current page or most visible page
        return layoutResult.currentPage
    }

    /**
     * Calculates animation duration based on distance and velocity
     */
    private fun calculateSnapDuration(distance: Float, velocity: PointF): Long {
        val velocityMagnitude = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)

        // Base duration
        var duration = snapConfig.snapDuration

        // Adjust based on distance
        val distanceFactor = MathUtils.clamp(distance / 1000f, 0.5f, 2f)
        duration = (duration * distanceFactor).toLong()

        // Adjust based on velocity (higher velocity = shorter duration)
        if (velocityMagnitude > 0) {
            val velocityFactor = MathUtils.clamp(1000f / velocityMagnitude, 0.3f, 1.5f)
            duration = (duration * velocityFactor).toLong()
        }

        return MathUtils.clamp(duration.toFloat(), 100f, 1000f).toLong()
    }

    /**
     * Checks if the current scroll position needs snapping
     */
    fun needsSnapping(currentScroll: PointF, velocity: PointF = PointF(0f, 0f)): Boolean {
        if (!snapConfig.enabled) return false

        val snapTarget = calculateSnapTarget(currentScroll, velocity) ?: return false

        // Check if we're close enough to snap
        return snapTarget.distance > 10f // 10px threshold
    }

    /**
     * Calculates eased position for smooth animation
     */
    fun calculateEasedPosition(
        startPosition: PointF,
        targetPosition: PointF,
        progress: Float
    ): PointF {
        val easedProgress = easeInOutCubic(progress, snapConfig.easingFactor)

        return PointF(
            MathUtils.lerp(startPosition.x, targetPosition.x, easedProgress),
            MathUtils.lerp(startPosition.y, targetPosition.y, easedProgress)
        )
    }

    /**
     * Cubic easing function
     */
    private fun easeInOutCubic(t: Float, strength: Float = 0.25f): Float {
        val adjustedT = MathUtils.clamp(t, 0f, 1f)
        val factor = 1f - strength

        return if (adjustedT < 0.5f) {
            factor * adjustedT + strength * 4f * adjustedT * adjustedT * adjustedT
        } else {
            val shifted = adjustedT - 1f
            factor * adjustedT + strength * (1f + 4f * shifted * shifted * shifted)
        }
    }

    /**
     * Gets snap configuration
     */
    fun getSnapConfig(): SnapConfig = snapConfig.copy()

    /**
     * Updates snap configuration
     */
    fun updateSnapConfig(newConfig: SnapConfig) {
        // In a real implementation, you would update the internal config
        // For now, this is a placeholder since snapConfig is val
    }
}