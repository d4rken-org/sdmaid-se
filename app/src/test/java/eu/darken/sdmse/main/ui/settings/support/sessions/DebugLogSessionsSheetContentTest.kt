package eu.darken.sdmse.main.ui.settings.support.sessions

import android.content.Context
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.io.File
import java.time.Instant

/**
 * Guards the layout half of the TV debug-log-sheet fix: when the session list is long, the
 * "Delete all debug logs?" footer must stay inside the sheet's height instead of being pushed
 * off the bottom edge. The `LazyColumn` carries `weight(1f, fill = false)` so it shrinks to the
 * space left after the header and footer; without it the list claims the whole sheet and the
 * footer overflows.
 *
 * The gesture half of the fix (disabling sheet drag/nested-scroll on TV so D-pad focus can't
 * slide the sheet) lives in `ModalBottomSheetSceneStrategy` and needs a real focus + nested-scroll
 * harness, so it is covered by on-device verification rather than this JVM test.
 */
class DebugLogSessionsSheetContentTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun finishedSessions(count: Int): List<DebugLogSession> = (0 until count).map { i ->
        DebugLogSession.Finished(
            id = SessionId("ext:done$i"),
            createdAt = Instant.now().minusSeconds(3600L * (i + 1)),
            logDir = File("/tmp/done$i"),
            diskSize = 8_000_000L,
            zipFile = File("/tmp/done$i.zip"),
            compressedSize = 1_024_000L,
        )
    }

    @Test
    fun `footer stays within the sheet height when the session list is long`() {
        val sheetHeight = 360.dp

        composeRule.setContent {
            PreviewWrapper {
                DebugLogSessionsSheetContent(
                    // requiredHeight pins the sheet height regardless of the Robolectric host
                    // size, so the weight cap is the only thing keeping the footer on screen.
                    modifier = Modifier
                        .requiredHeight(sheetHeight)
                        .testTag(CONTAINER_TAG),
                    stateSource = MutableStateFlow(
                        DebugLogSessionsViewModel.State(sessions = finishedSessions(10)),
                    ),
                )
            }
        }

        val container = composeRule.onNodeWithTag(CONTAINER_TAG).getUnclippedBoundsInRoot()
        val footer = composeRule
            .onNodeWithText(context.getString(R.string.support_debuglog_folder_delete_confirmation_title))
            .getUnclippedBoundsInRoot()

        withClue("footer bottom ${footer.bottom} must fit within sheet bottom ${container.bottom}") {
            (footer.bottom <= container.bottom + 1.dp) shouldBe true
        }
    }

    companion object {
        private const val CONTAINER_TAG = "debuglog-sheet"
    }
}
