package eu.darken.sdmse.exclusion.ui.editor.path

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class PathExclusionEditorScreenTest : BaseComposeRobolectricTest() {

    private fun readyState(tags: Set<Exclusion.Tag>) = PathExclusionViewModel.State.Ready(
        current = PathExclusion(
            path = LocalPath.build("storage", "emulated", "0", "DCIM"),
            tags = tags,
        ),
        lookup = null,
        canSave = true,
        canRemove = false,
    )

    private fun ComposeContentTestRule.setEditorScreen(
        stateSource: MutableStateFlow<PathExclusionViewModel.State>,
        onToggleTag: (Exclusion.Tag) -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                PathExclusionEditorScreen(
                    stateSource = stateSource,
                    onToggleTag = onToggleTag,
                )
            }
        }
    }

    private fun ComposeContentTestRule.squeezerToggle() =
        onNode(hasText("Media Squeeze") and isToggleable())

    @Test
    fun `clicking the Media Squeeze toggle reports the SQUEEZER tag`() {
        val toggled = mutableListOf<Exclusion.Tag>()
        composeRule.setEditorScreen(
            stateSource = MutableStateFlow(readyState(setOf(Exclusion.Tag.GENERAL))),
            onToggleTag = { toggled.add(it) },
        )

        composeRule.onNodeWithText("Media Squeeze").performScrollTo()
        composeRule.squeezerToggle().performClick()

        composeRule.waitForIdle()
        toggled shouldContainExactly listOf(Exclusion.Tag.SQUEEZER)
    }

    @Test
    fun `Media Squeeze toggle is on when the exclusion has the SQUEEZER tag`() {
        composeRule.setEditorScreen(
            stateSource = MutableStateFlow(readyState(setOf(Exclusion.Tag.SQUEEZER))),
        )

        composeRule.squeezerToggle().performScrollTo().assertIsOn()
    }

    @Test
    fun `Media Squeeze toggle follows state updates`() {
        val stateSource = MutableStateFlow<PathExclusionViewModel.State>(
            readyState(setOf(Exclusion.Tag.DEDUPLICATOR)),
        )
        composeRule.setEditorScreen(stateSource = stateSource)

        composeRule.squeezerToggle().performScrollTo().assertIsOff()

        stateSource.value = readyState(setOf(Exclusion.Tag.SQUEEZER))

        composeRule.squeezerToggle().performScrollTo().assertIsOn()
    }
}
