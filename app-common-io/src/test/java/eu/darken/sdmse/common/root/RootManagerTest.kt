package eu.darken.sdmse.common.root

import android.content.Context
import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.root.service.RootServiceClient
import eu.darken.sdmse.common.root.service.RootServiceConnection
import eu.darken.sdmse.common.sharedresource.Resource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import testhelpers.flow.test

class RootManagerTest : BaseTest() {

    private val context: Context = mockk(relaxed = true)
    private val serviceClient: RootServiceClient = mockk()
    private val settings: RootSettings = mockk()

    private val useRootValue: DataStoreValue<Boolean?> = mockk()
    private lateinit var useRootFlow: MutableStateFlow<Boolean?>
    private val connection: RootServiceClient.Connection = mockk()
    private val ipc: RootServiceConnection = mockk()
    private lateinit var scope: CoroutineScope

    /** How often the su host was actually asked for a connection. */
    private var probeCount = 0

    @BeforeEach
    fun setup() {
        probeCount = 0
        useRootFlow = MutableStateFlow(true)
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        every { settings.useRoot } returns useRootValue
        every { useRootValue.flow } returns useRootFlow

        every { connection.ipc } returns ipc
        every { ipc.checkBase() } returns "ok"
        coEvery { serviceClient.get() } answers {
            probeCount++
            Resource(connection, mockk(relaxed = true))
        }
    }

    @AfterEach
    fun teardown() {
        scope.cancel()
    }

    private fun manager(
        appScope: CoroutineScope = scope,
        dispatcher: CoroutineDispatcher? = null,
    ) = RootManager(
        context = context,
        appScope = appScope,
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        serviceClient = serviceClient,
        settings = settings,
    )

    private fun failingProbe() {
        coEvery { serviceClient.get() } answers {
            probeCount++
            throw RootUnavailableException("Root denied")
        }
    }

    @Test fun `isRooted memoises the probe for one setting and generation`() {
        val mgr = manager()

        runBlocking { mgr.isRooted() } shouldBe true
        runBlocking { mgr.isRooted() } shouldBe true

        probeCount shouldBe 1
    }

    @Test fun `refresh invalidates the memoised probe`() {
        val mgr = manager()

        runBlocking { mgr.isRooted() } shouldBe true
        mgr.refresh()
        runBlocking { mgr.isRooted() } shouldBe true

        probeCount shouldBe 2
    }

    @Test fun `a setting change invalidates the memoised probe`() {
        // The setting is part of the cache key, so no invalidation collector is needed.
        val mgr = manager()

        runBlocking { mgr.isRooted() } shouldBe true
        useRootFlow.value = false
        runBlocking { mgr.isRooted() } shouldBe true

        probeCount shouldBe 2
    }

    @Test fun `refresh does not block behind an in-flight probe and does not discard it`() = runTest2 {
        val gate = CompletableDeferred<Unit>()
        coEvery { serviceClient.get() } coAnswers {
            probeCount++
            gate.await()
            Resource(connection, mockk(relaxed = true))
        }
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val appScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val mgr = manager(appScope, testDispatcher)

        val inFlight = appScope.async { mgr.isRooted() }
        runCurrent()
        probeCount shouldBe 1

        // The running probe holds the cache lock across the su bind: refresh must return anyway.
        mgr.refresh()

        // ... and the probe it did not wait for must still deliver its own result.
        inFlight.isActive shouldBe true
        gate.complete(Unit)
        inFlight.await() shouldBe true
        probeCount shouldBe 1

        // The refresh is not lost either: the next probe runs under the new generation.
        mgr.isRooted() shouldBe true
        probeCount shouldBe 2

        appScope.cancel()
    }

    @Test fun `concurrent refreshes never lose a generation`() {
        // A SetupManager.refresh() and a Retry tap can land on the manager at the same moment. If the
        // generation counter lost an increment, the second caller would be served the first caller's
        // cached answer and its retry would silently do nothing. This needs real parallelism: refresh()
        // does not suspend, so a single-threaded test dispatcher can never interleave its
        // read-modify-write and would pass even against a racy `value += 1`.
        val mgr = manager()

        runBlocking(Dispatchers.Default) {
            (1..1000).map { launch { mgr.refresh() } }.joinAll()
        }

        mgr.currentGeneration shouldBe 1000
    }

    @Test fun `a probe that throws is cached as not-rooted and re-runs after refresh`() {
        failingProbe()
        val mgr = manager()

        runBlocking { mgr.isRooted() } shouldBe false
        runBlocking { mgr.isRooted() } shouldBe false
        probeCount shouldBe 1

        mgr.refresh()
        runBlocking { mgr.isRooted() } shouldBe false
        probeCount shouldBe 2
    }

    @Test fun `refresh makes accessState re-emit Checking then Active`() {
        val mgr = manager()

        val collector = mgr.accessState.test(tag = "accessState", scope = scope)
        collector.await { values, _ -> values.contains(AccessState.Active) }

        mgr.refresh()
        collector.await { values, _ -> values.count { it == AccessState.Active } == 2 }

        collector.latestValues shouldBe listOf(
            AccessState.Checking,
            AccessState.Active,
            AccessState.Checking,
            AccessState.Active,
        )

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `accessState walks from Unavailable to Active when a retry is granted`() {
        // The journey this branch exists for: the root manager denied (or timed out) the first
        // request, the user grants it and taps Retry. No test device is rooted, so this stands in
        // for the on-device check.
        failingProbe()
        val mgr = manager()

        val collector = mgr.accessState.test(tag = "accessState", scope = scope)
        collector.await { values, _ -> values.contains(AccessState.Unavailable) }
        collector.latestValues shouldBe listOf(
            AccessState.Checking,
            AccessState.Unavailable,
        )

        // Root works now, the user answered the manager's prompt on the second attempt.
        coEvery { serviceClient.get() } answers {
            probeCount++
            Resource(connection, mockk(relaxed = true))
        }
        mgr.refresh()
        collector.await { values, _ -> values.contains(AccessState.Active) }

        collector.latestValues shouldBe listOf(
            AccessState.Checking,
            AccessState.Unavailable,
            AccessState.Checking,
            AccessState.Active,
        )
        probeCount shouldBe 2

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `refresh makes useRoot re-emit when the probe outcome changes`() {
        failingProbe()
        val mgr = manager()

        val collector = mgr.useRoot.test(tag = "useRoot", scope = scope)
        collector.await { values, _ -> values.isNotEmpty() }
        collector.latestValues shouldBe listOf(false)

        // Root works now (e.g. the user answered the manager's prompt on the second attempt).
        coEvery { serviceClient.get() } answers {
            probeCount++
            Resource(connection, mockk(relaxed = true))
        }
        mgr.refresh()
        collector.await { values, _ -> values.contains(true) }

        collector.latestValues shouldBe listOf(false, true)

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `a retry that changes nothing does not re-emit useRoot`() {
        // useRoot is a StateFlow, so an unchanged outcome is conflated and nothing ripples downstream.
        val mgr = manager()

        val collector = mgr.useRoot.test(tag = "useRoot", scope = scope)
        // accessState shares the probe input and re-emits unconditionally, so its second Active is
        // the signal that the retry's probe finished — no wall-clock wait to prove useRoot stayed quiet.
        val access = mgr.accessState.test(tag = "accessState", scope = scope)
        collector.await { values, _ -> values.isNotEmpty() }
        access.await { values, _ -> values.contains(AccessState.Active) }

        mgr.refresh()
        access.await { values, _ -> values.count { it == AccessState.Active } == 2 }

        collector.latestValues shouldBe listOf(true)
        probeCount shouldBe 2

        runBlocking { collector.cancelAndJoin() }
        runBlocking { access.cancelAndJoin() }
    }

    @Test fun `resubscribing after the last subscriber left re-runs upstream instead of replaying`() {
        val mgr = manager()

        val first = mgr.accessState.test(tag = "first", scope = scope)
        first.await { values, _ -> values.contains(AccessState.Active) }
        runBlocking { first.cancelAndJoin() }

        // No wall-clock wait for the share to wind down: WhileSubscribed(replayExpiration=ZERO) drops
        // the replay cache as the last subscriber leaves, so the await below is what waits — for the
        // re-run's own Checking+Active rather than for a stopwatch.
        val second = mgr.accessState.test(tag = "second", scope = scope)
        second.await { values, _ ->
            values == listOf(AccessState.Checking, AccessState.Active)
        }

        // Not a replayed Active: the upstream ran again, starting at Checking.
        second.latestValues shouldBe listOf(AccessState.Checking, AccessState.Active)
        // The memoised answer is still valid though, so no second su bind.
        probeCount shouldBe 1

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `repeated refreshes do not multiply probes beyond one per generation`() {
        val mgr = manager()

        val access = mgr.accessState.test(tag = "accessState", scope = scope)
        val useRoot = mgr.useRoot.test(tag = "useRoot", scope = scope)
        access.await { values, _ -> values.contains(AccessState.Active) }
        useRoot.await { values, _ -> values.isNotEmpty() }
        probeCount shouldBe 1

        mgr.refresh()
        access.await { values, _ -> values.count { it == AccessState.Active } == 2 }
        probeCount shouldBe 2

        mgr.refresh()
        access.await { values, _ -> values.count { it == AccessState.Active } == 3 }
        probeCount shouldBe 3

        runBlocking { access.cancelAndJoin() }
        runBlocking { useRoot.cancelAndJoin() }
    }
}
