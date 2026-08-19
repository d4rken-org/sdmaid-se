package eu.darken.sdmse.setup

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.setup.inventory.InventorySetupCardItem
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.root.RootSetupCardItem
import eu.darken.sdmse.setup.root.RootSetupModule
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
    )

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
