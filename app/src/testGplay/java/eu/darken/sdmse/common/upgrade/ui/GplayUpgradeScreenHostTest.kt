package eu.darken.sdmse.common.upgrade.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoGplay
import eu.darken.sdmse.common.upgrade.core.billing.GplayServiceUnavailableException
import eu.darken.sdmse.common.upgrade.core.billing.Sku
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider

/**
 * Host-level counterpart to the ViewModel's `onResume` tests: those prove the ViewModel re-queries,
 * they cannot prove the screen actually asks it to. Only a real lifecycle round-trip catches a
 * missing or mis-scoped ON_RESUME effect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class GplayUpgradeScreenHostTest : BaseTest() {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun mockRepo(): UpgradeRepoGplay = mockk<UpgradeRepoGplay>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(false, null, null, isSettled = true))
        every { wasEverPro } returns MutableStateFlow(false)
        every { proUnconfirmedSince } returns MutableStateFlow(0L)
        every { autoRestoreBusy } returns MutableStateFlow(false)
        every { purchaseLaunchSku } returns MutableStateFlow<Sku?>(null)
    }

    @Test
    fun `a pending-purchase event puts the informational dialog on screen`() {
        // Host-level wiring: the ViewModel tests prove the event is emitted, only a real
        // composition proves it reaches a dialog (and survives as a rememberSaveable flag).
        val repo = mockRepo()
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = UpgradeViewModel(
            handle = SavedStateHandle(mapOf("forced" to false)),
            dispatcherProvider = TestDispatcherProvider(),
            upgradeRepo = repo,
            webpageTool = mockk(relaxed = true),
        )

        composeRule.setContent {
            PreviewWrapper {
                UpgradeScreenHost(vm = vm)
            }
        }
        composeRule.waitForIdle()

        vm.events.tryEmit(UpgradeEvents.PurchasePending)

        val message = composeRule.activity.getString(R.string.upgrade_screen_pending_dialog_message)
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(message, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `returning to the screen re-runs a failed offers query`() {
        val repo = mockRepo()
        var queries = 0
        coEvery { repo.querySkus(any()) } coAnswers {
            queries++
            throw GplayServiceUnavailableException(RuntimeException("Play hiccup"))
        }
        val vm = UpgradeViewModel(
            handle = SavedStateHandle(mapOf("forced" to false)),
            dispatcherProvider = TestDispatcherProvider(),
            upgradeRepo = repo,
            webpageTool = mockk(relaxed = true),
        )

        // The real ViewModel instance, passed explicitly: hiltViewModel() has nothing to resolve here.
        composeRule.setContent {
            PreviewWrapper {
                UpgradeScreenHost(vm = vm)
            }
        }

        composeRule.waitUntil { vm.state.value is GplayUpgradeUiState.Unavailable }
        val baseline = queries

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.waitUntil { queries > baseline }
    }
}
