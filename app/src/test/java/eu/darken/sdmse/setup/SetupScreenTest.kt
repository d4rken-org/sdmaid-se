package eu.darken.sdmse.setup

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.setup.automation.AutomationSetupCardItem
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.inventory.InventorySetupCardItem
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.notification.NotificationSetupCardItem
import eu.darken.sdmse.setup.notification.NotificationSetupModule
import eu.darken.sdmse.setup.root.RootSetupCardItem
import eu.darken.sdmse.setup.root.RootSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupCardItem
import eu.darken.sdmse.setup.storage.StorageSetupModule
import java.io.File
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.TestApplication
import eu.darken.sdmse.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SetupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `loading state shows progress indicator and no cards`() {
        composeRule.setSetupContent {
            SetupScreen(uiState = SetupUiState.Loading)
        }

        composeRule.onAllNodesWithText(context.getString(R.string.setup_notification_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_continue)).assertCountEquals(0)
    }

    @Test
    fun `complete state renders no cards and no continue button`() {
        // Complete triggers auto-nav in the Host; the Screen just shows a loading indicator.
        composeRule.setSetupContent {
            SetupScreen(uiState = SetupUiState.Complete)
        }

        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_continue)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_notification_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_saf_card_title)).assertCountEquals(0)
    }

    @Test
    fun `onboarding mode hides data areas menu entry`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(items = emptyList()),
                isOnboarding = true,
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.data_areas_label)).assertCountEquals(0)
    }

    @Test
    fun `loading card shows per-type title and loading hint`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(SetupLoadingCardItem(state = LoadingState(SetupModule.Type.SAF))),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_saf_card_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_progress_loading)).assertCountEquals(1)
    }

    @Test
    fun `notification card shows body and grant button when incomplete`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        NotificationSetupCardItem(
                            state = NotificationSetupModule.Result(
                                missingPermission = setOf(Permission.POST_NOTIFICATIONS),
                            ),
                            onGrantAction = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_notification_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_notification_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(1)
    }

    @Test
    fun `inventory card with fake access shows error label and open settings button`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        InventorySetupCardItem(
                            state = InventorySetupModule.Result(
                                missingPermission = emptySet(),
                                isAccessFaked = true,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_error_label)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_open_system_settings_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(0)
    }

    @Test
    fun `root card enable radio invokes onToggleUseRoot with true`() {
        var selection: Boolean? = null
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        RootSetupCardItem(
                            state = RootSetupModule.Result(useRoot = null),
                            onToggleUseRoot = { selection = it },
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onNode(hasText(context.getString(R.string.setup_root_enable_root_use_label))).performClick()
        composeRule.runOnIdle { assertTrue(selection == true) }
    }

    @Test
    fun `automation card with full consent shows enabled + running states and disallow action`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        AutomationSetupCardItem(
                            state = AutomationSetupModule.Result(
                                isNotRequired = false,
                                hasConsent = true,
                                canSelfEnable = false,
                                isServiceEnabled = true,
                                isServiceRunning = true,
                                isShortcutOrButtonEnabled = false,
                                needsXiaomiAutostart = false,
                                liftRestrictionsIntent = Intent(),
                                showAppOpsRestrictionHint = false,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onDismiss = {},
                            onHelp = {},
                            onRestrictionsHelp = {},
                            onRestrictionsShow = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_acs_state_enabled)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_acs_state_running)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_acs_consent_negative_action)).assertCountEquals(1)
    }

    @Test
    fun `storage card renders path labels`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        StorageSetupCardItem(
                            state = StorageSetupModule.Result(
                                paths = listOf(
                                    StorageSetupModule.Result.PathAccess(
                                        label = "Public storage".toCaString(),
                                        localPath = LocalPath.build(File("/storage/emulated/0")),
                                        hasAccess = false,
                                    ),
                                ),
                                missingPermission = setOf(Permission.READ_EXTERNAL_STORAGE),
                            ),
                            onPathClicked = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText("Public storage").assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(1)
    }

    private data class LoadingState(
        override val type: SetupModule.Type,
        override val startAt: Instant = Instant.now(),
    ) : SetupModule.State.Loading
}

private fun ComposeContentTestRule.setSetupContent(
    content: @Composable () -> Unit,
) {
    setContent {
        PreviewWrapper {
            content()
        }
    }
}
