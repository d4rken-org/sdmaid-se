package eu.darken.sdmse.corpsefinder.ui.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewLocalPathLookup
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class CorpseFinderListScreenTest : BaseComposeRobolectricTest() {

    // The list screen reads LocalGuidedTourController; supply a relaxed mock (shouldStart() defaults
    // to false, so no tour starts) — these tests stay focused on the list UI.
    private val mockTourController: GuidedTourController = mockk(relaxed = true)

    private fun row(name: String, size: Long = 1024L): CorpseFinderListViewModel.Row =
        CorpseFinderListViewModel.Row(
            corpse = previewCorpse(
                lookup = previewLocalPathLookup(
                    pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", name),
                    size = size,
                ),
                content = emptyList(),
            ),
        )

    private fun ComposeContentTestRule.setListScreen(state: CorpseFinderListViewModel.State) {
        setContent {
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController) {
                PreviewWrapper {
                    CorpseFinderListScreen(stateSource = MutableStateFlow(state))
                }
            }
        }
    }

    @Test
    fun `loading state shows no rows or empty marker`() {
        composeRule.setListScreen(CorpseFinderListViewModel.State(rows = null))

        // SdmEmptyState text "Empty" must not appear during loading.
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        // Tool name from the top bar is always visible.
        composeRule.onNodeWithText("CorpseFinder").assertExists()
    }

    @Test
    fun `empty state shows the Empty placeholder`() {
        composeRule.setListScreen(CorpseFinderListViewModel.State(rows = emptyList()))

        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `populated state renders each row by its file name`() {
        composeRule.setListScreen(
            CorpseFinderListViewModel.State(
                rows = listOf(row("alpha.dat"), row("beta.dat"), row("gamma.dat")),
            ),
        )

        composeRule.onNodeWithText("alpha.dat").assertExists()
        composeRule.onNodeWithText("beta.dat").assertExists()
        composeRule.onNodeWithText("gamma.dat").assertExists()
        // "Empty" placeholder must not render when rows are present.
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
    }

    @Test
    fun `markers info icon opens the markers dialog`() {
        composeRule.setListScreen(CorpseFinderListViewModel.State(rows = emptyList()))

        // Resolved string from corpsefinder strings.xml — `About these markers`. Before the
        // click it appears ONLY as the icon's contentDescription, not as text. After the
        // click the dialog body renders the title as Text content. Pinning the "before" state
        // catches a regression where the dialog was always rendered or the click was disabled.
        val markersLabel = "About these markers"
        composeRule.onAllNodesWithText(markersLabel).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(markersLabel).assertExists()

        composeRule.onNodeWithContentDescription(markersLabel).performClick()

        // After tapping, the dialog title text appears in the content tree.
        composeRule.onAllNodesWithText(markersLabel).fetchSemanticsNodes().size shouldBeAtLeastInt 1
    }

    @Test
    fun `tapping a row routes to onRowClick callback with that row`() {
        val first = row("first.dat")
        val second = row("second.dat")
        val clicked = mutableListOf<CorpseFinderListViewModel.Row>()
        composeRule.setContent {
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController) {
                PreviewWrapper {
                    CorpseFinderListScreen(
                        stateSource = MutableStateFlow(
                            CorpseFinderListViewModel.State(rows = listOf(first, second)),
                        ),
                        onRowClick = { clicked.add(it) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("second.dat").performClick()

        clicked.size shouldBeAtLeastInt 1
        clicked.last().corpse.lookup.lookedUp shouldBeEqual second.corpse.lookup.lookedUp
    }

    private infix fun Int.shouldBeAtLeastInt(min: Int) {
        if (this < min) throw AssertionError("Expected at least $min but was $this")
    }

    private infix fun <T> T.shouldBeEqual(other: T) {
        if (this != other) throw AssertionError("Expected <$other> but was <$this>")
    }
}
