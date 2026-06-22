package eu.darken.sdmse.appcontrol.ui.list.actions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppActionItem
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
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

class AppActionSheetTest : BaseComposeRobolectricTest() {

    private fun makeAppInfo(
        pkgName: String = "com.test.app",
        label: String = "Test App",
        versionName: String = "1.2.3",
        versionCode: Long = 42,
    ): AppInfo {
        val pkgId = Pkg.Id(pkgName)
        val installId = InstallId(pkgId, UserHandle2(0))
        val labelCa: CaString = label.toCaString()
        val pkg = mockk<Installed>(
            relaxed = true,
            moreInterfaces = arrayOf(InstallDetails::class),
        ).apply {
            every { id } returns pkgId
            every { this@apply.installId } returns installId
            every { this@apply.userHandle } returns UserHandle2(0)
            every { this@apply.label } returns labelCa
            every { packageName } returns pkgName
            every { this@apply.versionName } returns versionName
            every { this@apply.versionCode } returns versionCode
            every { (this@apply as InstallDetails).isEnabled } returns true
            every { (this@apply as InstallDetails).isSystemApp } returns false
            every { (this@apply as InstallDetails).installerInfo } returns mockk(relaxed = true)
        }
        return AppInfo(
            pkg = pkg,
            isActive = null,
            sizes = null,
            usage = null,
            userProfile = null,
            canBeToggled = true,
            canBeStopped = true,
            canBeExported = true,
            canBeDeleted = true,
            canBeArchived = false,
            canBeRestored = false,
        )
    }

    private fun ComposeContentTestRule.setSheet(
        state: AppActionViewModel.State,
        onActionTapped: (AppActionItem) -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                AppActionSheet(
                    stateSource = MutableStateFlow(state),
                    onActionTapped = onActionTapped,
                )
            }
        }
    }

    @Test
    fun `empty state renders a blank placeholder and no header text`() {
        // With no appInfo, the sheet shows only an empty placeholder Box. None of the action
        // titles should appear.
        composeRule.setSheet(AppActionViewModel.State())

        composeRule.onAllNodesWithText("Launch app").assertCountEquals(0)
        composeRule.onAllNodesWithText("Disable app").assertCountEquals(0)
    }

    @Test
    fun `populated state renders the app label, package name, and version line`() {
        val app = makeAppInfo(pkgName = "com.demo.app", label = "Demo", versionName = "9.8")

        composeRule.setSheet(
            AppActionViewModel.State(
                appInfo = app,
                items = emptyList(),
            ),
        )

        composeRule.onNodeWithText("Demo").assertExists()
        composeRule.onNodeWithText("com.demo.app").assertExists()
        // Version line: "<versionName> (<versionCode>)" — both portions are part of one Text node.
        composeRule.onNodeWithText("9.8 (42)").assertExists()
    }

    @Test
    fun `populated state renders each provided action item`() {
        val app = makeAppInfo()
        val items: List<AppActionItem> = listOf(
            AppActionItem.Action.Launch(app.installId),
            AppActionItem.Action.ForceStop(app.installId),
            AppActionItem.Action.Uninstall(app.installId),
        )

        composeRule.setSheet(
            AppActionViewModel.State(appInfo = app, items = items),
        )

        composeRule.onNodeWithText("Launch app").assertExists()
        composeRule.onNodeWithText("Force stop").assertExists()
        composeRule.onNodeWithText("Delete").assertExists()
    }

    @Test
    fun `Toggle action with isEnabled true renders Disable app row`() {
        // When the app is currently enabled, the toggle is presented as "Disable app".
        val app = makeAppInfo()
        composeRule.setSheet(
            AppActionViewModel.State(
                appInfo = app,
                items = listOf(AppActionItem.Action.Toggle(app.installId, isEnabled = true)),
            ),
        )

        composeRule.onNodeWithText("Disable app").assertExists()
        // "Enable app" should NOT be visible since the app is enabled.
        composeRule.onAllNodesWithText("Enable app").assertCountEquals(0)
    }

    @Test
    fun `Toggle action with isEnabled false renders Enable app row`() {
        val app = makeAppInfo()
        composeRule.setSheet(
            AppActionViewModel.State(
                appInfo = app,
                items = listOf(AppActionItem.Action.Toggle(app.installId, isEnabled = false)),
            ),
        )

        composeRule.onNodeWithText("Enable app").assertExists()
        composeRule.onAllNodesWithText("Disable app").assertCountEquals(0)
    }

    @Test
    fun `tapping an action invokes onActionTapped with that item`() {
        val app = makeAppInfo()
        val launchItem = AppActionItem.Action.Launch(app.installId)
        val tapped = mutableListOf<AppActionItem>()

        composeRule.setSheet(
            state = AppActionViewModel.State(appInfo = app, items = listOf(launchItem)),
            onActionTapped = { tapped.add(it) },
        )

        composeRule.onNodeWithText("Launch app").performClick()

        if (tapped.isEmpty()) throw AssertionError("Expected at least one tap callback but got none")
        tapped.last() shouldBeEqual launchItem
    }

    @Test
    fun `Archive item only renders when present in items list`() {
        // Archive visibility is gated by the AppActionItem.Action.Archive item being present.
        // Without it, "Archive" must not appear in the sheet.
        val app = makeAppInfo()
        composeRule.setSheet(
            AppActionViewModel.State(
                appInfo = app,
                items = listOf(AppActionItem.Action.Launch(app.installId)),
            ),
        )

        // "Archive" comes from the `appcontrol_archive_action` string; it must not render when no
        // Archive item is supplied.
        composeRule.onAllNodesWithText("Archive").assertCountEquals(0)
    }

    private infix fun <T> T.shouldBeEqual(other: T) {
        if (this != other) throw AssertionError("Expected <$other> but was <$this>")
    }
}
