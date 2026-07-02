package eu.darken.sdmse.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.setup.automation.AutomationSetupCardItem
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.inventory.InventorySetupCardItem
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.notification.NotificationSetupCardItem
import eu.darken.sdmse.setup.notification.NotificationSetupModule
import eu.darken.sdmse.setup.root.RootSetupCardItem
import eu.darken.sdmse.setup.root.RootSetupModule
import eu.darken.sdmse.setup.saf.SAFSetupCardItem
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.shizuku.ShizukuSetupCardItem
import eu.darken.sdmse.setup.shizuku.ShizukuSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupCardItem
import eu.darken.sdmse.setup.storage.StorageSetupModule
import io.mockk.mockk
import java.io.File
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.sdmse.common.R as CommonR

class SetupScreenTest : BaseComposeRobolectricTest() {

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
    fun `inventory card with fake access shows error label, limitation box and open settings button`() {
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
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_invalid_label)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_limitation_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_limitation_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_limitation_body2)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_help_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_open_system_settings_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(0)
    }

    @Test
    fun `inventory card limitation box buttons invoke settings and help actions`() {
        var settingsClicks = 0
        var helpClicks = 0
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
                            onGrantAction = { settingsClicks++ },
                            onHelp = { helpClicks++ },
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithText(context.getString(CommonR.string.general_open_system_settings_action)).performClick()
        composeRule.onNodeWithText(context.getString(CommonR.string.general_help_action)).performClick()
        composeRule.runOnIdle {
            assertTrue(settingsClicks == 1)
            // The container help icon has no text label, so the click unambiguously hit the box button.
            assertTrue(helpClicks == 1)
        }
    }

    @Test
    fun `inventory card with missing permission shows grant button and no limitation box`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        InventorySetupCardItem(
                            state = InventorySetupModule.Result(
                                missingPermission = setOf(Permission.GET_INSTALLED_APPS),
                                isAccessFaked = false,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_limitation_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_invalid_label)).assertCountEquals(0)
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
    fun `root card shows definitive not-available label when root probe failed`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        RootSetupCardItem(
                            state = RootSetupModule.Result(
                                useRoot = true,
                                isInstalled = false,
                                ourService = false,
                            ),
                            onToggleUseRoot = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }

        // The settled probe-failure state is definitive — no "?" guesswork is appended.
        val notAvailableText = context.getString(R.string.setup_root_state_waiting_label)
        composeRule.onAllNodesWithText(notAvailableText).assertCountEquals(1)
        composeRule.onAllNodesWithText("$notAvailableText ?").assertCountEquals(0)
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
                                showAdvancedProtectionHint = false,
                                isAdvancedProtectionBlocked = false,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onDismiss = {},
                            onHelp = {},
                            onRestrictionsHelp = {},
                            onRestrictionsShow = {},
                            onAdvancedProtectionHelp = {},
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
    fun `automation card advanced protection hint shows help only and no view action`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        AutomationSetupCardItem(
                            state = AutomationSetupModule.Result(
                                isNotRequired = false,
                                hasConsent = true,
                                canSelfEnable = false,
                                isServiceEnabled = false,
                                isServiceRunning = false,
                                isShortcutOrButtonEnabled = false,
                                needsXiaomiAutostart = false,
                                liftRestrictionsIntent = Intent(),
                                showAppOpsRestrictionHint = false,
                                showAdvancedProtectionHint = true,
                                isAdvancedProtectionBlocked = true,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onDismiss = {},
                            onHelp = {},
                            onRestrictionsHelp = {},
                            onRestrictionsShow = {},
                            onAdvancedProtectionHelp = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_acs_advanced_protection_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(eu.darken.sdmse.common.R.string.general_help_action)).assertCountEquals(1)
        // Advanced Protection has no per-app remedy, so the "View" action must not be offered here.
        composeRule.onAllNodesWithText(context.getString(eu.darken.sdmse.common.R.string.general_view_action)).assertCountEquals(0)
    }

    @Test
    fun `automation card advanced protection help button invokes callback`() {
        var helpClicks = 0
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        automationCardItem(
                            state = automationResult(showAdvancedProtectionHint = true, isAdvancedProtectionBlocked = true),
                            onAdvancedProtectionHelp = { helpClicks++ },
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithText(context.getString(CommonR.string.general_help_action)).performClick()
        composeRule.runOnIdle { assertTrue(helpClicks == 1) }
    }

    @Test
    fun `automation card appops restriction hint shows help and view actions that invoke callbacks`() {
        var helpClicks = 0
        var showClicks = 0
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        automationCardItem(
                            state = automationResult(showAppOpsRestrictionHint = true),
                            onRestrictionsHelp = { helpClicks++ },
                            onRestrictionsShow = { showClicks++ },
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_acs_appops_restriction_title)).assertCountEquals(1)
        composeRule.onNodeWithText(context.getString(CommonR.string.general_help_action)).performClick()
        composeRule.onNodeWithText(context.getString(CommonR.string.general_view_action)).performClick()
        composeRule.runOnIdle {
            assertTrue(helpClicks == 1)
            assertTrue(showClicks == 1)
        }
    }

    private fun automationResult(
        showAppOpsRestrictionHint: Boolean = false,
        showAdvancedProtectionHint: Boolean = false,
        isAdvancedProtectionBlocked: Boolean = false,
    ) = AutomationSetupModule.Result(
        isNotRequired = false,
        hasConsent = true,
        canSelfEnable = false,
        isServiceEnabled = false,
        isServiceRunning = false,
        isShortcutOrButtonEnabled = false,
        needsXiaomiAutostart = false,
        liftRestrictionsIntent = Intent(),
        showAppOpsRestrictionHint = showAppOpsRestrictionHint,
        showAdvancedProtectionHint = showAdvancedProtectionHint,
        isAdvancedProtectionBlocked = isAdvancedProtectionBlocked,
        settingsIntent = Intent(),
    )

    private fun automationCardItem(
        state: AutomationSetupModule.Result,
        onRestrictionsHelp: () -> Unit = {},
        onRestrictionsShow: () -> Unit = {},
        onAdvancedProtectionHelp: () -> Unit = {},
    ) = AutomationSetupCardItem(
        state = state,
        onGrantAction = {},
        onDismiss = {},
        onHelp = {},
        onRestrictionsHelp = onRestrictionsHelp,
        onRestrictionsShow = onRestrictionsShow,
        onAdvancedProtectionHelp = onAdvancedProtectionHelp,
    )

    @Test
    fun `shizuku card shows root info note and open action while waiting`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        ShizukuSetupCardItem(
                            state = ShizukuSetupModule.Result(
                                pkg = "moe.shizuku.privileged.api".toPkgId(),
                                useShizuku = true,
                                isCompatible = true,
                                isInstalled = true,
                                basicService = true,
                                ourService = false,
                                alsoHasRoot = true,
                            ),
                            onToggleUseShizuku = {},
                            onOpen = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }

        val expectedBody = buildString {
            append(context.getString(R.string.setup_shizuku_card_body))
            append("\n")
            append(context.getString(R.string.setup_shizuku_card_root_info))
        }
        composeRule.onAllNodesWithText(expectedBody).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_shizuku_state_waiting_label)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_shizuku_card_title)).assertCountEquals(2)
    }

    @Test
    fun `shizuku card hides open action once complete`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        ShizukuSetupCardItem(
                            state = ShizukuSetupModule.Result(
                                pkg = "moe.shizuku.privileged.api".toPkgId(),
                                useShizuku = true,
                                isCompatible = true,
                                isInstalled = true,
                                basicService = true,
                                ourService = true,
                                alsoHasRoot = false,
                            ),
                            onToggleUseShizuku = {},
                            onOpen = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.setup_shizuku_card_title)).assertCountEquals(1)
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

    @Test
    fun `storage card shows granted label per granted path and hides grant button when complete`() {
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
                                        hasAccess = true,
                                    ),
                                    StorageSetupModule.Result.PathAccess(
                                        label = "SD card".toCaString(),
                                        localPath = LocalPath.build(File("/storage/ABCD-EF12")),
                                        hasAccess = true,
                                    ),
                                ),
                                missingPermission = emptySet(),
                            ),
                            onPathClicked = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_granted_label)).assertCountEquals(2)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(0)
    }

    @Test
    fun `saf card shows mixed access state and keeps grant action while incomplete`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        SAFSetupCardItem(
                            state = SAFSetupModule.Result(
                                paths = listOf(
                                    SAFSetupModule.Result.PathAccess(
                                        label = "Public storage".toCaString(),
                                        safPath = SAFPath.build(
                                            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A"),
                                        ),
                                        localPath = LocalPath.build(File("/storage/emulated/0")),
                                        uriPermission = null,
                                        grantIntent = Intent(),
                                    ),
                                    SAFSetupModule.Result.PathAccess(
                                        label = "Public app data".toCaString(),
                                        safPath = SAFPath.build(
                                            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata"),
                                        ),
                                        localPath = LocalPath.build(File("/storage/emulated/0/Android/data")),
                                        uriPermission = fakeUriPermission(),
                                        grantIntent = Intent(),
                                    ),
                                ),
                            ),
                            onPathClicked = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_granted_label)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(1)
    }

    @Test
    fun `saf card hides grant action once all paths are granted`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        SAFSetupCardItem(
                            state = SAFSetupModule.Result(
                                paths = listOf(
                                    SAFSetupModule.Result.PathAccess(
                                        label = "Public storage".toCaString(),
                                        safPath = SAFPath.build(
                                            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A"),
                                        ),
                                        localPath = LocalPath.build(File("/storage/emulated/0")),
                                        uriPermission = fakeUriPermission(),
                                        grantIntent = Intent(),
                                    ),
                                ),
                            ),
                            onPathClicked = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_granted_label)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(0)
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

private fun fakeUriPermission(): android.content.UriPermission =
    mockk(relaxed = true)
