package eu.darken.sdmse.swiper.ui.swipe

import kotlin.math.abs

internal enum class SwipeOutcome { SnapBack, Keep, Delete, Skip, Undo }

internal const val SWIPE_VELOCITY_THRESHOLD = 1000f

internal fun decideSwipe(
    offsetX: Float,
    offsetY: Float,
    velocityX: Float,
    velocityY: Float,
    threshold: Float,
    canUndo: Boolean,
    swapDirections: Boolean,
    velocityThreshold: Float = SWIPE_VELOCITY_THRESHOLD,
): SwipeOutcome {
    val absX = abs(offsetX)
    val absY = abs(offsetY)
    val absVx = abs(velocityX)
    val absVy = abs(velocityY)
    val horizontalDominant = absX >= absY
    return if (horizontalDominant) {
        val committed = absX > threshold || absVx > velocityThreshold
        if (!committed) {
            SwipeOutcome.SnapBack
        } else if (offsetX > 0f) {
            if (swapDirections) SwipeOutcome.Delete else SwipeOutcome.Keep
        } else {
            if (swapDirections) SwipeOutcome.Keep else SwipeOutcome.Delete
        }
    } else {
        val committed = absY > threshold || absVy > velocityThreshold
        if (!committed) {
            SwipeOutcome.SnapBack
        } else if (offsetY < 0f) {
            SwipeOutcome.Skip
        } else if (canUndo) {
            SwipeOutcome.Undo
        } else {
            SwipeOutcome.SnapBack
        }
    }
}
