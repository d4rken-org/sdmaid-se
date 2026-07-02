package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanTask
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanner
import eu.darken.sdmse.analyzer.core.storage.StorageScanner
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.MediaStoreTool
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.stats.core.SpaceTracker
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

/**
 * The auto-rescan that heals stale `setupIncomplete` flags runs unattended on the app scope,
 * which has no exception handler — anything escaping it kills the process. These tests pin the
 * containment and the trigger conditions.
 */
class AnalyzerAutoRescanTest : BaseTest() {

    private val setupState = MutableStateFlow<SetupModule.State>(setupModuleState(isComplete = false))

    private fun setupModuleState(isComplete: Boolean): SetupModule.State.Current = mockk {
        every { this@mockk.isComplete } returns isComplete
    }

    private fun storage(stale: Boolean) = DeviceStorage(
        id = StorageId(internalId = null, externalId = UUID.randomUUID()),
        label = "storage".toCaString(),
        type = DeviceStorage.Type.PRIMARY,
        hardware = DeviceStorage.Hardware.BUILT_IN,
        spaceCapacity = 100L,
        spaceFree = 50L,
        setupIncomplete = stale,
    )

    private class Harness(
        val analyzer: Analyzer,
        val appScope: CoroutineScope,
        val uncaught: MutableList<Throwable>,
    )

    private fun TestScope.createHarness(scanner: DeviceStorageScanner): Harness {
        val uncaught = mutableListOf<Throwable>()
        val appScope = CoroutineScope(
            SupervisorJob() +
                UnconfinedTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, e -> uncaught += e },
        )
        val analyzer = Analyzer(
            appScope = appScope,
            deviceScanner = Provider { scanner },
            storageScanner = Provider { mockk<StorageScanner>() },
            gatewaySwitch = mockk<GatewaySwitch> {
                every { sharedResource } returns SharedResource.createKeepAlive("test:gateway", appScope)
            },
            appInventorySetupModule = mockk { every { state } returns emptyFlow() },
            storageSetupModule = mockk { every { state } returns setupState },
            mediaStoreTool = mockk<MediaStoreTool>(relaxed = true),
            spaceTracker = mockk<SpaceTracker>(relaxed = true),
        )
        return Harness(analyzer, appScope, uncaught)
    }

    private fun scanner(scanCalls: AtomicInteger, onScan: suspend (Int) -> Set<DeviceStorage>) =
        mockk<DeviceStorageScanner> {
            every { progress } returns flowOf(null)
            coEvery { scan() } coAnswers { onScan(scanCalls.incrementAndGet()) }
        }

    @Test
    fun `a failing auto-rescan is contained instead of reaching the app scope`() = runTest {
        val scanCalls = AtomicInteger()
        val harness = createHarness(
            scanner(scanCalls) { call ->
                when (call) {
                    1 -> setOf(storage(stale = true))
                    else -> throw IOException("binder died mid-scan")
                }
            },
        )
        try {
            // Manual scan while setup is incomplete caches stale data.
            harness.analyzer.submit(DeviceStorageScanTask())
            scanCalls.get() shouldBe 1

            // Setup completes -> auto-rescan fires and blows up. Without containment this is an
            // uncaught exception on the app scope, i.e. process death.
            setupState.value = setupModuleState(isComplete = true)
            advanceUntilIdle()

            scanCalls.get() shouldBe 2
            harness.uncaught.shouldBeEmpty()
        } finally {
            harness.appScope.cancel()
        }
    }

    @Test
    fun `setup completing mid-scan still triggers the healing rescan`() = runTest {
        val scanCalls = AtomicInteger()
        val firstScanGate = CompletableDeferred<Unit>()
        val harness = createHarness(
            scanner(scanCalls) { call ->
                when (call) {
                    1 -> {
                        firstScanGate.await()
                        // Baked while setup was still incomplete at scan start.
                        setOf(storage(stale = true))
                    }
                    else -> setOf(storage(stale = false))
                }
            },
        )
        try {
            val firstScan = launch { harness.analyzer.submit(DeviceStorageScanTask()) }
            runCurrent()
            scanCalls.get() shouldBe 1

            // Setup completes while the scan is in flight: cached devices are still empty at this
            // moment, so a trigger that only samples the current value would do nothing - the
            // stale result published after the transition must re-evaluate the condition.
            setupState.value = setupModuleState(isComplete = true)
            runCurrent()

            firstScanGate.complete(Unit)
            firstScan.join()
            advanceUntilIdle()

            scanCalls.get() shouldBe 2
            harness.analyzer.data.first().storages.none { it.setupIncomplete } shouldBe true
            harness.uncaught.shouldBeEmpty()
        } finally {
            harness.appScope.cancel()
        }
    }

    @Test
    fun `no rescan when setup completes without stale data`() = runTest {
        val scanCalls = AtomicInteger()
        val harness = createHarness(scanner(scanCalls) { setOf(storage(stale = false)) })
        try {
            setupState.value = setupModuleState(isComplete = true)
            advanceUntilIdle()

            scanCalls.get() shouldBe 0
            harness.uncaught.shouldBeEmpty()
        } finally {
            harness.appScope.cancel()
        }
    }

    @Test
    fun `successful rescan does not loop`() = runTest {
        val scanCalls = AtomicInteger()
        val harness = createHarness(
            scanner(scanCalls) { call ->
                when (call) {
                    1 -> setOf(storage(stale = true))
                    else -> setOf(storage(stale = false))
                }
            },
        )
        try {
            harness.analyzer.submit(DeviceStorageScanTask())
            setupState.value = setupModuleState(isComplete = true)
            advanceUntilIdle()

            scanCalls.get() shouldBe 2
            harness.analyzer.data.first().storages.none { it.setupIncomplete } shouldBe true
            harness.uncaught.shouldBeEmpty()
        } finally {
            harness.appScope.cancel()
        }
    }
}
