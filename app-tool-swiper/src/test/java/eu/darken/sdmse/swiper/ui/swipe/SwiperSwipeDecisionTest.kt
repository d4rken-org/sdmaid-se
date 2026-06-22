package eu.darken.sdmse.swiper.ui.swipe

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SwiperSwipeDecisionTest : BaseTest() {

    private val threshold = 400f

    @Test
    fun `low offset and low velocity snap back`() {
        decideSwipe(
            offsetX = 50f, offsetY = 30f,
            velocityX = 100f, velocityY = 80f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.SnapBack
    }

    @Test
    fun `right past distance threshold commits Keep`() {
        decideSwipe(
            offsetX = threshold + 1f, offsetY = 0f,
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Keep
    }

    @Test
    fun `left past distance threshold commits Delete`() {
        decideSwipe(
            offsetX = -(threshold + 1f), offsetY = 0f,
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Delete
    }

    @Test
    fun `velocity flick right commits Keep even below distance threshold`() {
        decideSwipe(
            offsetX = 50f, offsetY = 0f,
            velocityX = SWIPE_VELOCITY_THRESHOLD + 100f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Keep
    }

    @Test
    fun `velocity flick left commits Delete even below distance threshold`() {
        decideSwipe(
            offsetX = -50f, offsetY = 0f,
            velocityX = -(SWIPE_VELOCITY_THRESHOLD + 100f), velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Delete
    }

    @Test
    fun `swapDirections inverts horizontal mapping`() {
        decideSwipe(
            offsetX = threshold + 1f, offsetY = 0f,
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = true,
        ) shouldBe SwipeOutcome.Delete

        decideSwipe(
            offsetX = -(threshold + 1f), offsetY = 0f,
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = true,
        ) shouldBe SwipeOutcome.Keep
    }

    @Test
    fun `up past threshold commits Skip`() {
        decideSwipe(
            offsetX = 0f, offsetY = -(threshold + 1f),
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Skip
    }

    @Test
    fun `down past threshold commits Undo when canUndo`() {
        decideSwipe(
            offsetX = 0f, offsetY = threshold + 1f,
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Undo
    }

    @Test
    fun `down past threshold snaps back when canUndo is false`() {
        decideSwipe(
            offsetX = 0f, offsetY = threshold + 1f,
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = false, swapDirections = false,
        ) shouldBe SwipeOutcome.SnapBack
    }

    @Test
    fun `velocity flick down with canUndo commits Undo`() {
        decideSwipe(
            offsetX = 0f, offsetY = 50f,
            velocityX = 0f, velocityY = SWIPE_VELOCITY_THRESHOLD + 100f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Undo
    }

    @Test
    fun `velocity flick down with canUndo false snaps back`() {
        decideSwipe(
            offsetX = 0f, offsetY = 50f,
            velocityX = 0f, velocityY = SWIPE_VELOCITY_THRESHOLD + 100f,
            threshold = threshold, canUndo = false, swapDirections = false,
        ) shouldBe SwipeOutcome.SnapBack
    }

    @Test
    fun `diagonal drag with horizontal dominant uses horizontal mapping`() {
        decideSwipe(
            offsetX = threshold + 1f, offsetY = threshold * 0.5f,
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Keep
    }

    @Test
    fun `diagonal drag with vertical dominant uses vertical mapping`() {
        decideSwipe(
            offsetX = threshold * 0.5f, offsetY = -(threshold + 1f),
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Skip
    }

    @Test
    fun `equal absolute offsets prefer horizontal axis`() {
        decideSwipe(
            offsetX = threshold + 1f, offsetY = -(threshold + 1f),
            velocityX = 0f, velocityY = 0f,
            threshold = threshold, canUndo = true, swapDirections = false,
        ) shouldBe SwipeOutcome.Keep
    }
}
