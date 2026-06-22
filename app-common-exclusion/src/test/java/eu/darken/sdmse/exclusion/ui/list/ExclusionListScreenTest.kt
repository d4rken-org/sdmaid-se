package eu.darken.sdmse.exclusion.ui.list

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class ExclusionListScreenTest : BaseComposeRobolectricTest() {

    private fun pkgRow(
        label: String,
        pkgName: String,
        isDefault: Boolean,
    ): ExclusionListViewModel.Row.Pkg = ExclusionListViewModel.Row.Pkg(
        exclusion = PkgExclusion(pkgId = pkgName.toPkgId()),
        pkg = null,
        isDefault = isDefault,
        reasonUrl = if (isDefault) "https://example.com/reason" else null,
        label = label,
    )

    private fun ComposeContentTestRule.setListScreen(
        state: ExclusionListViewModel.State,
        onRemoveSelected: (Set<ExclusionId>) -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                ExclusionListScreen(
                    stateSource = MutableStateFlow(state),
                    onRemoveSelected = onRemoveSelected,
                )
            }
        }
    }

    @Test
    fun `long-pressing a default exclusion enters selection mode`() {
        composeRule.setListScreen(
            ExclusionListViewModel.State(
                rows = listOf(pkgRow(label = "Default App", pkgName = "com.default.app", isDefault = true)),
                showDefaults = true,
            ),
        )

        composeRule.onNodeWithText("Default App").performTouchInput { longClick() }

        // Selection top bar replaces the normal top bar and shows the count.
        composeRule.onNodeWithText("1 selected").assertExists()
    }

    @Test
    fun `deleting a long-pressed default exclusion reports its id`() {
        val removed = mutableListOf<Set<ExclusionId>>()
        composeRule.setListScreen(
            state = ExclusionListViewModel.State(
                rows = listOf(pkgRow(label = "Default App", pkgName = "com.default.app", isDefault = true)),
                showDefaults = true,
            ),
            onRemoveSelected = { removed.add(it) },
        )

        composeRule.onNodeWithText("Default App").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Delete selected").performClick()

        removed.size shouldBe 1
        removed.single() shouldContainExactly setOf(PkgExclusion.createId("com.default.app".toPkgId()))
    }

    @Test
    fun `long-pressing a user exclusion still enters selection mode`() {
        composeRule.setListScreen(
            ExclusionListViewModel.State(
                rows = listOf(pkgRow(label = "User App", pkgName = "com.user.app", isDefault = false)),
            ),
        )

        composeRule.onNodeWithText("User App").performTouchInput { longClick() }

        composeRule.onNodeWithText("1 selected").assertExists()
    }

    @Test
    fun `restore defaults option is hidden when defaults are not modified`() {
        composeRule.setListScreen(
            ExclusionListViewModel.State(
                rows = listOf(pkgRow(label = "User App", pkgName = "com.user.app", isDefault = false)),
                defaultsModified = false,
            ),
        )

        composeRule.onNodeWithContentDescription("Options").performClick()
        composeRule.onNodeWithText("Restore default exclusions").assertDoesNotExist()
    }

    @Test
    fun `restore defaults option is shown when defaults are modified`() {
        composeRule.setListScreen(
            ExclusionListViewModel.State(
                rows = listOf(pkgRow(label = "User App", pkgName = "com.user.app", isDefault = false)),
                defaultsModified = true,
            ),
        )

        composeRule.onNodeWithContentDescription("Options").performClick()
        composeRule.onNodeWithText("Restore default exclusions").assertExists()
    }
}
