package eu.darken.sdmse.appcontrol.ui.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class AppControlListScreenTest : BaseComposeRobolectricTest() {

    private fun row(
        pkgName: String,
        label: String = pkgName,
    ): AppControlListViewModel.Row {
        val pkgId = Pkg.Id(pkgName)
        val userHandle = UserHandle2(0)
        val installId = InstallId(pkgId, userHandle)
        val labelCa: CaString = label.toCaString()
        val pkg = mockk<Installed>(
            relaxed = true,
            moreInterfaces = arrayOf(InstallDetails::class),
        ).apply {
            every { id } returns pkgId
            every { this@apply.installId } returns installId
            every { this@apply.userHandle } returns userHandle
            every { this@apply.label } returns labelCa
            every { packageName } returns pkgName
            every { (this@apply as InstallDetails).isEnabled } returns true
            every { (this@apply as InstallDetails).isSystemApp } returns false
            every { (this@apply as InstallDetails).installerInfo } returns mockk(relaxed = true)
        }
        return AppControlListViewModel.Row(
            appInfo = AppInfo(
                pkg = pkg,
                isActive = null,
                sizes = null,
                usage = null,
                userProfile = null,
                canBeToggled = false,
                canBeStopped = false,
                canBeExported = false,
                canBeDeleted = false,
                canBeArchived = false,
                canBeRestored = false,
            ),
            sectionKeyName = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            sectionKeyPkg = pkgName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        )
    }

    // The list screen reads LocalGuidedTourController via `LocalGuidedTourController.current`.
    // In production it's installed by the Activity composition. For unit tests we supply a relaxed
    // mock — the tour-start condition (`shouldStart`) defaults to `false` so no UI tour is shown.
    private val mockTourController: GuidedTourController = mockk<GuidedTourController>(relaxed = true).apply {
        every { session } returns MutableStateFlow<eu.darken.sdmse.common.compose.tour.TourSession?>(null)
    }

    private fun ComposeContentTestRule.setListScreen(state: AppControlListViewModel.State) {
        setContent {
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController) {
                PreviewWrapper {
                    AppControlListScreen(stateSource = MutableStateFlow(state))
                }
            }
        }
    }

    @Test
    fun `loading state shows no Empty placeholder`() {
        // Initial state: rows = null. The screen renders a blank placeholder Box, no "Empty" text.
        composeRule.setListScreen(AppControlListViewModel.State(rows = null))

        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        // Top bar tool name is always visible.
        composeRule.onNodeWithText("AppControl").assertExists()
    }

    @Test
    fun `empty state shows the Empty placeholder`() {
        composeRule.setListScreen(AppControlListViewModel.State(rows = emptyList()))

        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `populated state renders each row by its label`() {
        composeRule.setListScreen(
            AppControlListViewModel.State(
                rows = listOf(
                    row("com.alpha.app", label = "Alpha"),
                    row("com.beta.app", label = "Beta"),
                    row("com.gamma.app", label = "Gamma"),
                ),
            ),
        )

        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onNodeWithText("Beta").assertExists()
        composeRule.onNodeWithText("Gamma").assertExists()
        // Each row shows its package name as the bodySmall caption — pin one to prove the row
        // composable is actually composing the package text and not just the title.
        composeRule.onNodeWithText("com.alpha.app").assertExists()
        // Empty placeholder must NOT render.
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
    }

    @Test
    fun `tapping a row routes to onTapRow callback with that row's installId`() {
        val alpha = row("com.alpha.app", label = "Alpha")
        val beta = row("com.beta.app", label = "Beta")
        val tapped = mutableListOf<InstallId>()

        composeRule.setContent {
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController) {
                PreviewWrapper {
                    AppControlListScreen(
                        stateSource = MutableStateFlow(
                            AppControlListViewModel.State(rows = listOf(alpha, beta)),
                        ),
                        onTapRow = { tapped.add(it) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Beta").performClick()

        if (tapped.isEmpty()) throw AssertionError("Expected at least one tap callback but got none")
        // Last tap is for Beta — guards against the row builder using the wrong index.
        tapped.last() shouldBeEqual beta.installId
    }

    @Test
    fun `subtitle shows the populated row count`() {
        // The top bar subtitle uses the plural string `result_x_items`. For two rows this resolves
        // to the English plural "2 items". Pinning the exact format guards against a regression
        // that swaps the placeholder or formats the count differently.
        composeRule.setListScreen(
            AppControlListViewModel.State(
                rows = listOf(
                    row("com.alpha.app", label = "Alpha"),
                    row("com.beta.app", label = "Beta"),
                ),
            ),
        )

        composeRule.onNodeWithText("2 items").assertExists()
    }

    @Test
    fun `filter row default state shows User and Enabled chips`() {
        // The filter row reflects the active filter tags as chips. Production default is
        // {USER, ENABLED} (see FilterSettings()).
        composeRule.setListScreen(
            AppControlListViewModel.State(
                rows = emptyList(),
                options = AppControlListViewModel.DisplayOptions(
                    listFilter = FilterSettings(
                        tags = setOf(FilterSettings.Tag.USER, FilterSettings.Tag.ENABLED),
                    ),
                    listSort = SortSettings(),
                ),
            ),
        )

        composeRule.onNodeWithText("User").assertExists()
        composeRule.onNodeWithText("Enabled").assertExists()
    }

    @Test
    fun `filter row and top bar actions are hidden while a task is executing`() {
        val filterOptions = AppControlListViewModel.DisplayOptions(
            listFilter = FilterSettings(
                tags = setOf(FilterSettings.Tag.USER, FilterSettings.Tag.ENABLED),
            ),
        )
        composeRule.setListScreen(
            AppControlListViewModel.State(
                rows = listOf(row("com.alpha.app", label = "Alpha")),
                progress = Progress.Data(),
                options = filterOptions,
            ),
        )

        // Filter chips are gone while the progress overlay covers the list.
        composeRule.onAllNodesWithText("User").assertCountEquals(0)
        composeRule.onAllNodesWithText("Enabled").assertCountEquals(0)
        // Search and overflow actions are gone too — refresh/filter/sort would act on stale data.
        composeRule.onAllNodesWithContentDescription("Search").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Options").assertCountEquals(0)
    }

    @Test
    fun `filter row and top bar actions are visible when no task is executing`() {
        composeRule.setListScreen(
            AppControlListViewModel.State(
                rows = listOf(row("com.alpha.app", label = "Alpha")),
                options = AppControlListViewModel.DisplayOptions(
                    listFilter = FilterSettings(tags = setOf(FilterSettings.Tag.USER)),
                ),
            ),
        )

        composeRule.onNodeWithText("User").assertExists()
        composeRule.onNodeWithContentDescription("Search").assertExists()
    }

    @Test
    fun `filter row is hidden on first load before rows arrive`() {
        // Cold start: progress is still null (not yet busy) but rows haven't loaded. The row must
        // stay hidden so it doesn't briefly appear and then animate out once the initial scan's
        // progress propagates (the first-load flash). Gated on rows != null in the screen.
        composeRule.setListScreen(
            AppControlListViewModel.State(
                rows = null,
                progress = null,
                options = AppControlListViewModel.DisplayOptions(
                    listFilter = FilterSettings(tags = setOf(FilterSettings.Tag.USER)),
                ),
            ),
        )

        composeRule.onAllNodesWithText("User").assertCountEquals(0)
        // Only the filter row is gated — the top bar (tool name) still renders.
        composeRule.onNodeWithText("AppControl").assertExists()
    }

    @Test
    fun `inspection mode renders populated rows without a tour controller`() {
        // In @Preview/inspection mode no real LocalGuidedTourController is provided; PreviewWrapper supplies
        // NoOpGuidedTourAccess when LocalInspectionMode is true, so the screen reads a no-op instead of hitting
        // the local's error() default. Deliberately do NOT provide LocalGuidedTourController here.
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PreviewWrapper {
                    AppControlListScreen(
                        stateSource = MutableStateFlow(
                            AppControlListViewModel.State(
                                rows = listOf(
                                    row("com.alpha.app", label = "Alpha"),
                                    row("com.beta.app", label = "Beta"),
                                ),
                            ),
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onNodeWithText("Beta").assertExists()
    }

    private infix fun <T> T.shouldBeEqual(other: T) {
        if (this != other) throw AssertionError("Expected <$other> but was <$this>")
    }
}
