package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.swiper.ui.preview.previewSwipeItem
import eu.darken.sdmse.swiper.ui.swipe.SwipeOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SwiperDeckCardTest : BaseComposeRobolectricTest() {

    private class Harness {
        var openClicks = 0
        var previewClicks = 0
        var committed: SwipeOutcome? = null
        var touchSlop = 0f
        var cardWidth = 0
    }

    private fun setCard(): Harness {
        val harness = Harness()
        composeRule.setContent {
            harness.touchSlop = LocalViewConfiguration.current.touchSlop
            // Renders FilePreviewImage's placeholder branch instead of issuing a real Coil request,
            // whose async completion could recompose mid-gesture.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PreviewWrapper {
                    SwiperDeckCard(
                        modifier = Modifier.onSizeChanged { harness.cardWidth = it.width },
                        item = previewSwipeItem(),
                        isTop = true,
                        canUndo = true,
                        swapDirections = false,
                        showDetails = true,
                        sessionPosition = 3,
                        totalItems = 12,
                        onCommit = { outcome, _, _ -> harness.committed = outcome },
                        onPreviewClick = { harness.previewClicks++ },
                        onOpenExternallyClick = { harness.openClicks++ },
                    )
                }
            }
        }
        return harness
    }

    @Test
    fun `sub-slop movement does not cancel the open-externally click`() {
        val harness = setCard()

        composeRule.onNodeWithContentDescription("Open in external app").performTouchInput {
            down(center)
            moveBy(Offset(1f, 1f))
            up()
        }

        composeRule.runOnIdle { assertEquals(1, harness.openClicks) }
    }

    @Test
    fun `sub-slop movement does not cancel the fullscreen-preview click`() {
        val harness = setCard()

        composeRule.onNodeWithContentDescription("View").performTouchInput {
            down(center)
            moveBy(Offset(1f, 1f))
            up()
        }

        composeRule.runOnIdle { assertEquals(1, harness.previewClicks) }
    }

    @Test
    fun `movement just past slop claims the gesture without committing`() {
        val harness = setCard()

        composeRule.onNodeWithContentDescription("Open in external app").performTouchInput {
            down(center)
            moveBy(Offset(harness.touchSlop + 1f, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(0, harness.openClicks)
            assertEquals(null, harness.committed)
        }
    }

    @Test
    fun `a slow multi-event drag still commits a swipe`() {
        val harness = setCard()

        composeRule.onNodeWithContentDescription("Open in external app").performTouchInput {
            down(center)
            // Five samples so the Lsq2 velocity tracker has enough data, spaced far enough apart to
            // stay below SWIPE_VELOCITY_THRESHOLD: the commit comes from the accumulated 0.6x width.
            moveBy(Offset(harness.cardWidth * 0.12f, 0f), delayMillis = 400)
            moveBy(Offset(harness.cardWidth * 0.12f, 0f), delayMillis = 400)
            moveBy(Offset(harness.cardWidth * 0.12f, 0f), delayMillis = 400)
            moveBy(Offset(harness.cardWidth * 0.12f, 0f), delayMillis = 400)
            moveBy(Offset(harness.cardWidth * 0.12f, 0f), delayMillis = 400)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(SwipeOutcome.Keep, harness.committed)
            assertEquals(0, harness.openClicks)
        }
    }

    @Test
    fun `a gesture started during the snap-back is judged on its own drag`() {
        val harness = setCard()
        val card = composeRule.onNodeWithContentDescription("Open in external app")
        composeRule.mainClock.autoAdvance = false

        // Gesture 1: a third of the card in a single move event, so the Lsq2 tracker reports no
        // velocity and the 0.33x drag stays under the 0.4x threshold. Release starts a snap-back.
        card.performTouchInput {
            down(center)
            moveBy(Offset(harness.touchSlop + harness.cardWidth * 0.33f, 0f), delayMillis = 400)
            up()
        }

        // Gesture 2 presses while that spring is in flight, then holds still long enough for it to
        // finish: the card is drawn back at zero before this drag ever crosses slop.
        composeRule.mainClock.advanceTimeByFrame()
        card.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(600)
        card.performTouchInput {
            moveBy(Offset(harness.touchSlop + harness.cardWidth * 0.1f, 0f), delayMillis = 400)
            up()
        }

        composeRule.mainClock.autoAdvance = true
        composeRule.runOnIdle {
            assertEquals("0.1x drag must not commit", null, harness.committed)
        }
    }
}
