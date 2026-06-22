package eu.darken.sdmse.common.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * Smoke coverage for [FocusHighlightOverlay]: PreviewWrapper applies SdmSeTheme, which mounts
 * the overlay around all content. Verifies the wrapper stays layout/interaction-neutral:
 * content clicks, focuses, and renders without issue while focus bounds are being observed.
 */
class FocusHighlightOverlayTest : BaseComposeRobolectricTest() {

    @Test
    fun `content under focus highlight overlay still clicks and focuses`() {
        var clicks = 0
        val requester = FocusRequester()
        composeRule.setContent {
            PreviewWrapper {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .focusRequester(requester)
                            .clickable { clicks++ }
                            .testTag("TARGET"),
                    )
                }
            }
        }
        composeRule.runOnIdle { requester.requestFocus() }
        composeRule.onNodeWithTag("TARGET").assertIsFocused()
        composeRule.onNodeWithTag("TARGET").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
