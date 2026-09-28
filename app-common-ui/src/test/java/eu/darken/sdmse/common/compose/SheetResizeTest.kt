package eu.darken.sdmse.common.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

// Animations off, as with "Remove animations" and on the emulator test runs.
class SheetResizeTest : BaseComposeRobolectricTest(
    effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    },
) {

    @Test
    fun `sheet shows all of its content after a loading placeholder grows into the loaded content`() {
        var loaded by mutableStateOf(false)
        var contentBottom = -1f
        var windowHeight = 0
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            PreviewWrapper {
                SdmModalBottomSheet(onDismiss = {}) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (loaded) 400.dp else 200.dp)
                            .onGloballyPositioned {
                                contentBottom = it.positionInWindow().y + it.size.height
                                windowHeight = it.findRootCoordinates().size.height
                            },
                    )
                }
            }
        }
        // Growing one frame in lands in the frame the opening animation finishes; other frames don't hit it.
        composeRule.mainClock.advanceTimeByFrame()
        loaded = true
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()

        assertEquals(windowHeight.toFloat(), contentBottom, 1f)
    }
}
