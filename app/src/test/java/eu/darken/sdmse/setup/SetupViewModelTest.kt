package eu.darken.sdmse.setup

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.setup.inventory.InventorySetupCardItem
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.root.RootSetupCardItem
import eu.darken.sdmse.setup.root.RootSetupModule
import eu.darken.sdmse.common.adb.shizuku.ShizukuServiceState
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.setup.shizuku.AdbManagerInstallGuide
import eu.darken.sdmse.setup.shizuku.ShizukuSetupCardItem
import eu.darken.sdmse.setup.shizuku.ShizukuSetupModule
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SetupViewModelTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()
    private val setupManager: SetupManager = mockk(relaxed = true)
    private val rootSetupModule: RootSetupModule = mockk(relaxed = true)
    private val inventorySetupModule: InventorySetupModule = mockk(relaxed = true)

    // Stands in for whichever flavor binding is compiled: the test source set sees neither impl.
    private val installGuide = object : AdbManagerInstallGuide {
        override val labelRes = R.string.setup_shizuku_install_manager_label
        override val url = "https://example.test/install"
        override val porterHelpUrl = "https://example.test/help"
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun setupState(vararg moduleStates: SetupModule.State) {
        every { setupManager.state } returns flowOf(
            SetupManager.State(
                moduleStates = moduleStates.toList(),
                isDismissed = false,
                isHealerWorking = false,
            )
        )
    }

    private fun buildVm() = SetupViewModel(
        handle = SavedStateHandle(),
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        context = ApplicationProvider.getApplicationContext<Context>(),
        setupManager = setupManager,
        storageSetupModule = mockk(relaxed = true),
        safSetupModule = mockk(relaxed = true),
        automationSetupModule = mockk(relaxed = true),
        webpageTool = mockk(relaxed = true),
        rootSetupModule = rootSetupModule,
        shizukuSetupModule = mockk(relaxed = true),
        inventorySetupModule = inventorySetupModule,
        deviceDetective = mockk(relaxed = true),
        adbManagerInstallGuide = installGuide,
    )

    @Test
    fun `shizuku card open falls back to the app info page without a launcher entry`() =
        runTest2(context = testDispatcher) {
            // Nothing registers a launcher activity for this package in the test environment, which is
            // the Shizuku+ Compat Hub case: the button must still land somewhere the user can act.
            setupState(
                ShizukuSetupModule.Result(
                    pkg = "moe.shizuku.privileged.api".toPkgId(),
                    useShizuku = true,
                    isCompatible = true,
                    isInstalled = true,
                    // Not Available on purpose: a complete card is filtered out of the render state,
                    // and the button exists in this state too.
                    serviceState = ShizukuServiceState.NotChecked,
                ),
            )
            val vm = buildVm()

            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect() }
            advanceUntilIdle()

            val cards = vm.uiState.value.shouldBeInstanceOf<SetupUiState.Cards>()
            cards.items.filterIsInstance<ShizukuSetupCardItem>().single().onOpen()
            advanceUntilIdle()

            val started = shadowOf(ApplicationProvider.getApplicationContext<Application>())
                .nextStartedActivity
            started.shouldNotBeNull()
            started.action shouldBe Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            started.data?.schemeSpecificPart shouldBe "moe.shizuku.privileged.api"
        }

    @Test
    fun `root card retry refreshes the root setup module`() = runTest2(context = testDispatcher) {
        setupState(
            RootSetupModule.Result(
                useRoot = true,
                isInstalled = true,
                ourService = false,
            ),
        )
        val vm = buildVm()

        // Keep the render state subscribed, otherwise WhileSubscribed never runs the upstream.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect() }
        advanceUntilIdle()

        val cards = vm.uiState.value.shouldBeInstanceOf<SetupUiState.Cards>()
        val rootCard = cards.items.filterIsInstance<RootSetupCardItem>().single()

        rootCard.onRetry()
        advanceUntilIdle()

        coVerify(exactly = 1) { rootSetupModule.refresh() }
    }

    @Test
    fun `inventory card retry refreshes the inventory setup module`() = runTest2(context = testDispatcher) {
        setupState(
            InventorySetupModule.Result(
                missingPermission = emptySet(),
                access = InventorySetupModule.InventoryAccess.ProbeFailed,
                settingsIntent = Intent(),
            ),
        )
        val vm = buildVm()

        // Keep the render state subscribed, otherwise WhileSubscribed never runs the upstream.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect() }
        advanceUntilIdle()

        val cards = vm.uiState.value.shouldBeInstanceOf<SetupUiState.Cards>()
        val inventoryCard = cards.items.filterIsInstance<InventorySetupCardItem>().single()

        inventoryCard.onRetry()
        advanceUntilIdle()

        coVerify(exactly = 1) { inventorySetupModule.refresh() }
    }
}
