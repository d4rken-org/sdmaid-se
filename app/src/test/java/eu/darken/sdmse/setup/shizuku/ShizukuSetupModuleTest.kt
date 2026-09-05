package eu.darken.sdmse.setup.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.shizuku.ShizukuManager
import eu.darken.sdmse.common.adb.shizuku.ShizukuServiceState
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.adb.shizuku.ShizukuBaseServiceBinder
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.setup.SetupModule
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.flow.test
import java.util.concurrent.CountDownLatch

class ShizukuSetupModuleTest : BaseTest() {

    private val context: Context = mockk()
    private val packageManager: PackageManager = mockk()
    private val adbSettings: AdbSettings = mockk()
    private val shizukuManager: ShizukuManager = mockk()
    private val dataAreaManager: DataAreaManager = mockk(relaxed = true)
    private val rootManager: RootManager = mockk()

    private val useShizukuValue: DataStoreValue<Boolean?> = mockk()
    private lateinit var useShizukuFlow: MutableStateFlow<Boolean?>
    private lateinit var scope: CoroutineScope
    private var probeCount = 0

    @BeforeEach
    fun setup() {
        probeCount = 0
        useShizukuFlow = MutableStateFlow(true)
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        every { context.packageManager } returns packageManager
        every { packageManager.getLaunchIntentForPackage(any()) } returns mockk<Intent>()

        every { adbSettings.useShizuku } returns useShizukuValue
        every { useShizukuValue.flow } returns useShizukuFlow

        every { shizukuManager.shizukuPkgId } returns "moe.shizuku.privileged.api".toPkgId()
        every { shizukuManager.shizukuBinder } returns flowOf(null)
        every { shizukuManager.permissionGrantEvents } returns emptyFlow()
        coEvery { shizukuManager.getManagerId() } returns "moe.shizuku.privileged.api".toPkgId()
        coEvery { shizukuManager.managerIds() } returns setOf("moe.shizuku.privileged.api".toPkgId())
        coEvery { shizukuManager.isCompatible() } returns true
        coEvery { shizukuManager.isGranted() } returns true
        coEvery { shizukuManager.getServiceState() } coAnswers { probeCount++; ShizukuServiceState.Available }

        every { rootManager.useRoot } returns flowOf(false)
    }

    @AfterEach
    fun teardown() {
        scope.cancel()
    }

    private fun module(
        moduleScope: CoroutineScope = scope,
        dispatchers: DispatcherProvider = TestDispatcherProvider(),
    ) = ShizukuSetupModule(
        context,
        moduleScope,
        dispatchers,
        adbSettings,
        shizukuManager,
        dataAreaManager,
        rootManager,
    )

    @Test fun `first subscription emits Loading then Result`() {
        val mod = module()

        val collector = mod.state.test(tag = "first", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }

        collector.latestValues.first().shouldBeInstanceOf<ShizukuSetupModule.Loading>()
        val result = collector.latestValues.last().shouldBeInstanceOf<ShizukuSetupModule.Result>()
        result.ourService shouldBe true

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `re-subscription emits cached Result instead of Loading`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) } // let the share fully stop (clears the replay buffer)

        // Returning to the dashboard: the cached Result must come first so the setup card doesn't
        // flicker to Loading while the probe re-runs.
        val second = mod.state.test(tag = "second", scope = scope)
        second.await { values, _ -> values.isNotEmpty() }

        second.latestValues.first().shouldBeInstanceOf<ShizukuSetupModule.Result>()

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `re-subscription still re-runs the probe in the background`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) }

        val before = probeCount
        val second = mod.state.test(tag = "second", scope = scope)
        second.await { _, _ -> probeCount > before } // doesn't trust the cache blindly

        probeCount shouldBeGreaterThan before

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `setting change while unsubscribed does not replay stale cache`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) }

        // User turns Shizuku off while nothing observes the module.
        useShizukuFlow.value = false

        val second = mod.state.test(tag = "second", scope = scope)
        second.await { values, _ -> values.isNotEmpty() }

        // Cached Result was for useShizuku=true and must not be replayed for the new setting.
        second.latestValues.first().shouldBeInstanceOf<ShizukuSetupModule.Loading>()

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `refresh triggers a fresh probe`() {
        val mod = module()

        val collector = mod.state.test(tag = "refresh", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        val before = probeCount

        runBlocking { mod.refresh() }
        collector.await { _, _ -> probeCount > before }

        probeCount shouldBeGreaterThan before

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `a wedged pingBinder still produces a Result`() {
        // pingBinder() is a synchronous PING_TRANSACTION. Against a Shizuku server that is alive but
        // not servicing requests it never returns, and unbounded it would stall this module's combine
        // so the setup card sits on Loading forever.
        val wedge = CountDownLatch(1)
        val binder = mockk<ShizukuBaseServiceBinder>()
        every { binder.pingBinder() } answers { wedge.await(); true }
        every { shizukuManager.shizukuBinder } returns flowOf(binder)

        // Real scope + real IO dispatcher: the wedge blocks an actual thread, Unconfined would run it
        // inline and block the collector before any timeout could apply.
        val realScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val mod = module(realScope, TestDispatcherProvider(Dispatchers.IO)).apply { pingTimeoutMs = 250L }

            val collector = mod.state.test(tag = "wedge", scope = realScope)
            collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }

            val result = collector.latestValues.filterIsInstance<ShizukuSetupModule.Result>().last()
            result.basicService shouldBe false

            runBlocking { collector.cancelAndJoin() }
        } finally {
            wedge.countDown()
            realScope.cancel()
        }
    }

    // --- service state -------------------------------------------------------------------------

    @Test fun `the probe announces itself before it settles`() {
        // A cold bind can take the whole ADB connect budget. Without an in-flight state the card
        // keeps offering a retry button that silently does nothing for those seconds.
        val mod = module()

        val collector = mod.state.test(tag = "checking", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result && !it.isChecking } }

        val results = collector.latestValues.filterIsInstance<ShizukuSetupModule.Result>()
        results.first().isChecking shouldBe true
        results.last().isChecking shouldBe false

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `only Available counts as our service being up`() {
        fun resultWith(state: ShizukuServiceState) = ShizukuSetupModule.Result(
            pkg = "moe.shizuku.privileged.api".toPkgId(),
            useShizuku = true,
            serviceState = state,
        )

        resultWith(ShizukuServiceState.Available).ourService shouldBe true
        resultWith(ShizukuServiceState.NotChecked).ourService shouldBe false
        resultWith(ShizukuServiceState.PermissionDenied).ourService shouldBe false
        resultWith(ShizukuServiceState.Unknown).ourService shouldBe false
        resultWith(ShizukuServiceState.TimedOut).ourService shouldBe false
        resultWith(ShizukuServiceState.Failed).ourService shouldBe false
    }

    @Test fun `isComplete truth table across every service outcome`() {
        fun complete(
            useShizuku: Boolean?,
            isCompatible: Boolean = true,
            isInstalled: Boolean = true,
            state: ShizukuServiceState = ShizukuServiceState.NotChecked,
        ) = ShizukuSetupModule.Result(
            pkg = "moe.shizuku.privileged.api".toPkgId(),
            useShizuku = useShizuku,
            isCompatible = isCompatible,
            isInstalled = isInstalled,
            serviceState = state,
        ).isComplete

        // Opted in and working is the only complete "on" state.
        complete(true, state = ShizukuServiceState.Available) shouldBe true
        complete(true, state = ShizukuServiceState.TimedOut) shouldBe false
        complete(true, state = ShizukuServiceState.Failed) shouldBe false
        complete(true, state = ShizukuServiceState.PermissionDenied) shouldBe false
        complete(true, state = ShizukuServiceState.Unknown) shouldBe false
        complete(true, state = ShizukuServiceState.NotChecked) shouldBe false

        // Wants Shizuku but it isn't installed stays incomplete, even when nothing failed.
        complete(true, isInstalled = false, state = ShizukuServiceState.Available) shouldBe false

        // Opted out, or Shizuku too old to use, are both settled states.
        complete(false, state = ShizukuServiceState.TimedOut) shouldBe true
        complete(true, isCompatible = false, state = ShizukuServiceState.TimedOut) shouldBe true
        complete(null, isCompatible = false) shouldBe true
    }


    @Test fun `a probe that throws settles as Failed instead of stranding the checking state`() {
        // Regression guard: emitting isChecking=true and THEN throwing kills the sharing coroutine
        // with that state stuck in replayingShare's replay slot. No refresh can replace it, so every
        // later subscriber inherits a permanently disabled retry button. pingBinder() is a binder
        // call and runDetachedWithTimeout propagates whatever its block throws, so this is reachable.
        val binder: ShizukuBaseServiceBinder = mockk()
        every { binder.pingBinder() } throws RuntimeException("binder died")
        every { shizukuManager.shizukuBinder } returns flowOf(binder)
        val mod = module()

        val collector = mod.state.test(tag = "throwing", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result && !it.isChecking } }

        val settled = collector.latestValues
            .filterIsInstance<ShizukuSetupModule.Result>()
            .last { !it.isChecking }
        settled.serviceState shouldBe ShizukuServiceState.Failed
        settled.isChecking shouldBe false

        runBlocking { collector.cancelAndJoin() }
    }

    // --- card package --------------------------------------------------------------------------

    private fun firstResult(mod: ShizukuSetupModule): ShizukuSetupModule.Result {
        val collector = mod.state.test(tag = "pkg", scope = scope)
        collector.await { values, _ -> values.any { it is ShizukuSetupModule.Result } }
        val result = collector.latestValues.filterIsInstance<ShizukuSetupModule.Result>().first()
        runBlocking { collector.cancelAndJoin() }
        return result
    }

    @Test fun `card package prefers the first manager that can be opened`() {
        // Shizuku+ next to its Compat Hub: the Hub owns the stock permission but has no launcher
        // activity, so opening it from the card would do nothing.
        coEvery { shizukuManager.getManagerId() } returns "moe.shizuku.privileged.api".toPkgId()
        coEvery { shizukuManager.managerIds() } returns setOf(
            "moe.shizuku.privileged.api".toPkgId(),
            "af.shizuku.plus.api".toPkgId(),
        )
        every { packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") } returns null
        every { packageManager.getLaunchIntentForPackage("af.shizuku.plus.api") } returns mockk<Intent>()

        firstResult(module()).pkg shouldBe "af.shizuku.plus.api".toPkgId()
    }

    @Test fun `card package falls back to the detected manager when none can be opened`() {
        coEvery { shizukuManager.getManagerId() } returns "moe.shizuku.privileged.api".toPkgId()
        coEvery { shizukuManager.managerIds() } returns setOf(
            "moe.shizuku.privileged.api".toPkgId(),
            "af.shizuku.plus.api".toPkgId(),
        )
        every { packageManager.getLaunchIntentForPackage(any()) } returns null

        firstResult(module()).pkg shouldBe "moe.shizuku.privileged.api".toPkgId()
    }

    @Test fun `card package is the reference package when nothing is installed`() {
        coEvery { shizukuManager.getManagerId() } returns null

        firstResult(module()).pkg shouldBe "moe.shizuku.privileged.api".toPkgId()

        verify(exactly = 0) { packageManager.getLaunchIntentForPackage(any()) }
    }

}
