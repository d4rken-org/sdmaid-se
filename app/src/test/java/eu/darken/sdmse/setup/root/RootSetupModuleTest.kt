package eu.darken.sdmse.setup.root

import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.flow.test

class RootSetupModuleTest : BaseTest() {

    private val rootSettings: RootSettings = mockk()
    private val rootManager: RootManager = mockk()
    private val dataAreaManager: DataAreaManager = mockk(relaxed = true)

    private val useRootValue: DataStoreValue<Boolean?> = mockk()
    private lateinit var useRootFlow: MutableStateFlow<Boolean?>
    private lateinit var accessFlow: MutableStateFlow<AccessState>
    private lateinit var scope: CoroutineScope

    @BeforeEach
    fun setup() {
        useRootFlow = MutableStateFlow(true)
        accessFlow = MutableStateFlow(AccessState.Active)
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        every { rootSettings.useRoot } returns useRootValue
        every { useRootValue.flow } returns useRootFlow
        coEvery { useRootValue.update(any()) } returns DataStoreValue.Updated(old = true, new = false)

        coEvery { rootManager.isInstalled() } returns true
        every { rootManager.accessState } returns accessFlow
        every { rootManager.refresh() } just runs
    }

    @AfterEach
    fun teardown() {
        scope.cancel()
    }

    private fun module() = RootSetupModule(scope, rootSettings, rootManager, dataAreaManager)

    @Test fun `first subscription emits Loading then Result`() {
        val mod = module()

        val collector = mod.state.test(tag = "first", scope = scope)
        collector.await { values, _ -> values.any { it is RootSetupModule.Result } }

        collector.latestValues.first().shouldBeInstanceOf<RootSetupModule.Loading>()
        val result = collector.latestValues.last().shouldBeInstanceOf<RootSetupModule.Result>()
        result.useRoot shouldBe true
        result.ourService shouldBe true

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `re-subscription emits cached Result instead of Loading`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is RootSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) }

        val second = mod.state.test(tag = "second", scope = scope)
        second.await { values, _ -> values.isNotEmpty() }

        second.latestValues.first().shouldBeInstanceOf<RootSetupModule.Result>()

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `setting change while unsubscribed does not replay stale cache`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is RootSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) }

        // User turns root off while nothing observes the module.
        useRootFlow.value = false

        val second = mod.state.test(tag = "second", scope = scope)
        second.await { values, _ -> values.isNotEmpty() }

        second.latestValues.first().shouldBeInstanceOf<RootSetupModule.Loading>()

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `a running probe keeps the module on Loading`() {
        // Acquiring the root host cold-binds a su session. While that runs the module must report
        // Loading - never a settled Result(ourService=false), which previously flagged setup as
        // incomplete and flashed the dashboard setup card on every launch.
        accessFlow.value = AccessState.Checking
        val mod = module()

        val collector = mod.state.test(tag = "checking", scope = scope)
        collector.await { values, _ -> values.size >= 2 }

        collector.latestValues.none { it is RootSetupModule.Result } shouldBe true

        accessFlow.value = AccessState.Active
        collector.await { values, _ -> values.any { it is RootSetupModule.Result } }

        val result = collector.latestValues.last().shouldBeInstanceOf<RootSetupModule.Result>()
        result.ourService shouldBe true
        result.isComplete shouldBe true

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `a failed probe surfaces an incomplete Result`() {
        // The user opted in and a root manager is installed, but our service never came up. That must
        // surface as an incomplete Result so the setup screen shows the real problem.
        accessFlow.value = AccessState.Unavailable
        val mod = module()

        val collector = mod.state.test(tag = "failure", scope = scope)
        collector.await { values, _ -> values.any { it is RootSetupModule.Result } }

        val result = collector.latestValues.last().shouldBeInstanceOf<RootSetupModule.Result>()
        result.useRoot shouldBe true
        result.ourService shouldBe false
        result.isComplete shouldBe false

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `refresh re-runs the root probe`() {
        val mod = module()

        runBlocking { mod.refresh() }

        verify(exactly = 1) { rootManager.refresh() }
    }

    @Test fun `completion depends on user choice and service readiness`() {
        RootSetupModule.Result(useRoot = null).isComplete shouldBe false
        RootSetupModule.Result(useRoot = false).isComplete shouldBe true
        RootSetupModule.Result(useRoot = true, isInstalled = true, ourService = false).isComplete shouldBe false
        RootSetupModule.Result(useRoot = true, isInstalled = false, ourService = false).isComplete shouldBe true
        RootSetupModule.Result(useRoot = true, isInstalled = true, ourService = true).isComplete shouldBe true
    }

    @Test fun `data areas reload when root becomes available`() {
        accessFlow.value = AccessState.Unavailable
        val mod = module()

        val collector = mod.state.test(tag = "gained", scope = scope)
        collector.await { values, _ -> values.any { it is RootSetupModule.Result } }
        coVerify(exactly = 0) { dataAreaManager.reload() }

        accessFlow.value = AccessState.Active
        collector.await { values, _ ->
            values.filterIsInstance<RootSetupModule.Result>().any { it.ourService }
        }

        coVerify(exactly = 1) { dataAreaManager.reload() }

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `data areas reload when root is lost`() {
        val mod = module()

        val collector = mod.state.test(tag = "lost", scope = scope)
        collector.await { values, _ -> values.any { it is RootSetupModule.Result } }

        accessFlow.value = AccessState.Unavailable
        collector.await { values, _ ->
            values.filterIsInstance<RootSetupModule.Result>().any { !it.ourService }
        }

        coVerify(exactly = 1) { dataAreaManager.reload() }

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `data areas do not reload on the first observation`() {
        val mod = module()

        val collector = mod.state.test(tag = "initial", scope = scope)
        collector.await { values, _ -> values.any { it is RootSetupModule.Result } }
        runBlocking { delay(50) }

        // Nothing has changed yet, so there is nothing to re-detect.
        coVerify(exactly = 0) { dataAreaManager.reload() }

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `data areas do not reload when a retry fails`() {
        accessFlow.value = AccessState.Unavailable
        val mod = module()

        val collector = mod.state.test(tag = "retry", scope = scope)
        collector.await { values, _ -> values.any { it is RootSetupModule.Result } }

        // Retry: probe runs again and fails again, so the effective access state never changed.
        accessFlow.value = AccessState.Checking
        collector.await { values, _ -> values.size >= 3 }
        accessFlow.value = AccessState.Unavailable
        collector.await { values, _ -> values.filterIsInstance<RootSetupModule.Result>().size >= 2 }

        coVerify(exactly = 0) { dataAreaManager.reload() }

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `toggleUseRoot no longer reloads data areas directly`() {
        val mod = module()

        runBlocking { mod.toggleUseRoot(false) }
        runBlocking { delay(50) }

        coVerify(exactly = 1) { useRootValue.update(any()) }
        // Reloading is owned by the state pipeline's transition observer, which only fires on a real
        // change of root availability.
        coVerify(exactly = 0) { dataAreaManager.reload() }
    }
}
