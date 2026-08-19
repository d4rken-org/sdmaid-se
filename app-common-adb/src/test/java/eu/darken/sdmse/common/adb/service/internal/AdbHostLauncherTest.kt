package eu.darken.sdmse.common.adb.service.internal

import android.os.IBinder
import android.os.IInterface
import eu.darken.sdmse.common.adb.AdbConnectTimeoutException
import eu.darken.sdmse.common.adb.AdbException
import eu.darken.sdmse.common.adb.service.AdbHostOptions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import testhelpers.coroutine.TestDispatcherProvider
import java.util.concurrent.CountDownLatch
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

/**
 * Unit coverage for [AdbHostLauncher.createConnection]'s teardown/orchestration. Shizuku is replaced
 * with a fake via the injectable seam (AdbHostLauncherSeam.kt).
 */
class AdbHostLauncherTest {

    private val events = mutableListOf<String>()

    private inner class FakeService(
        val onUnbind: () -> Unit = {},
        val onAwait: suspend () -> Unit = {},
    ) : ShizukuUserService {
        override fun bind() {
            events += "bind"
        }

        override fun unbind() {
            events += "unbind"
            onUnbind()
        }

        override suspend fun awaitDisconnect() {
            events += "awaitDisconnect"
            onAwait()
        }
    }

    private inner class FakeFactory(
        val service: ShizukuUserService = FakeService(),
        val version: Int = 11,
        val bindError: Throwable? = null,
        val handshakeError: Throwable? = null,
        /** Blocks apiVersion() to model a wedged Shizuku.getVersion() binder transaction. */
        val versionWedge: CountDownLatch? = null,
        val versionEntered: CompletableDeferred<Unit>? = null,
    ) : ShizukuUserServiceFactory {
        /** Captured so a test can simulate an unexpected onServiceDisconnected. */
        var disconnectCallback: (() -> Unit)? = null

        /** Captured so a test can simulate onServiceConnected. */
        var connectedCallback: ((IBinder?) -> Unit)? = null

        override fun apiVersion(): Int {
            versionEntered?.complete(Unit)
            versionWedge?.await() // blocks the calling thread, like a wedged binder transaction
            return version
        }

        override fun <Host : AdbConnection> create(
            hostClass: KClass<Host>,
            options: AdbHostOptions,
            onConnected: (IBinder?) -> Unit,
            onDisconnected: () -> Unit,
        ): ShizukuUserService {
            connectedCallback = onConnected
            disconnectCallback = onDisconnected
            return if (bindError != null) {
                object : ShizukuUserService by service {
                    override fun bind() {
                        events += "bind"
                        throw bindError
                    }
                }
            } else {
                service
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <Service : IInterface, Host : AdbConnection> handshake(
            binder: IBinder?,
            serviceClass: KClass<Service>,
            options: AdbHostOptions,
        ): Pair<Service, Host> {
            events += "handshake"
            handshakeError?.let { throw it }
            return (mockk<AdbConnection>() as Service) to (mockk<AdbConnection>() as Host)
        }
    }

    private fun TestScope.launcher(factory: ShizukuUserServiceFactory) = AdbHostLauncher(
        serviceFactory = factory,
        appScope = backgroundScope,
        dispatcherProvider = TestDispatcherProvider(),
    )

    private fun AdbHostLauncher.connect(
        connectTimeoutMs: Long = AdbHostLauncher.CONNECT_TIMEOUT_MS,
        apiVersionTimeoutMs: Long = AdbHostLauncher.API_VERSION_TIMEOUT_MS,
        unbindTimeoutMs: Long = AdbHostLauncher.UNBIND_TIMEOUT_MS,
    ) = createConnection(
        apiVersionTimeoutMs = apiVersionTimeoutMs,
        unbindTimeoutMs = unbindTimeoutMs,
        serviceClass = AdbConnection::class,
        hostClass = AdbConnection::class,
        // Explicit values: AdbHostOptions()'s default isDebug=BuildConfigWrap.DEBUG triggers
        // BuildConfigWrap's static init, which isn't available on a plain JVM.
        options = AdbHostOptions(isDebug = false, isTrace = false, isDryRun = false, recorderPath = null),
        connectTimeoutMs = connectTimeoutMs,
    )

    /**
     * Bounded await that fails loudly on timeout.
     *
     * withTimeout throws a TimeoutCancellationException, and a CancellationException escaping a
     * test body unwinds it as a *cancellation* rather than a failure — so a wedge regression these
     * tests exist to catch can silently pass. withTimeoutOrNull + an explicit null check turns
     * "never settled" into a real assertion failure.
     */
    private suspend fun <T : Any> awaitOrFail(what: String, block: suspend () -> T): T {
        val settled = withTimeoutOrNull(5_000L) { block() }
        return withClue("$what did not settle within 5s") { settled.shouldNotBeNull() }
    }

    @Test fun `unsupported shizuku version fails before binding`() = runTest {
        val l = launcher(FakeFactory(version = 9))

        shouldThrow<IllegalStateException> { l.connect().collect { } }

        events.shouldBeEmpty() // never bound
    }

    @Test fun `cancel unbinds then waits for disconnect`() = runTest {
        val l = launcher(FakeFactory(FakeService()))

        val job = launch { l.connect().collect { } }
        // runCurrent, not advanceUntilIdle: the fakes never connect, so advancing virtual time would
        // trip the connect-watchdog instead of testing the cancellation teardown.
        runCurrent() // reach awaitClose (bound)
        job.cancelAndJoin()

        events shouldContainInOrder listOf("bind", "unbind", "awaitDisconnect")
    }

    @Test fun `a failing unbind is best-effort and still awaits disconnect`() = runTest {
        val l = launcher(FakeFactory(FakeService(onUnbind = { throw IllegalStateException("unbind boom") })))

        val job = launch { l.connect().collect { } }
        runCurrent()
        job.cancelAndJoin() // must not throw

        events shouldContainInOrder listOf("bind", "unbind", "awaitDisconnect")
    }

    @Test fun `a hanging disconnect is bounded`() = runTest {
        val l = launcher(FakeFactory(FakeService(onAwait = { awaitCancellation() }))) // never disconnects

        val job = launch { l.connect().collect { } }
        runCurrent()
        job.cancelAndJoin() // runTest advances virtual time through the bounded await

        events shouldContainInOrder listOf("bind", "unbind", "awaitDisconnect")
    }

    @Test fun `unexpected disconnect closes the connection and still tears down`() = runTest {
        val factory = FakeFactory(FakeService())
        val l = launcher(factory)

        val caught = CompletableDeferred<Throwable>()
        val job = launch {
            try {
                l.connect().collect { }
            } catch (e: Throwable) {
                caught.complete(e)
            }
        }
        runCurrent() // reach awaitClose (bound)

        factory.disconnectCallback!!.invoke() // simulate an unexpected onServiceDisconnected
        advanceUntilIdle()

        caught.await().shouldBeInstanceOf<AdbException>() // flow closed instead of leaking a dead connection
        events shouldContainInOrder listOf("bind", "unbind") // finally still unbound
        job.cancelAndJoin()
    }

    @Test fun `a failing bind does not attempt unbind`() = runTest {
        val l = launcher(FakeFactory(bindError = IllegalStateException("bind boom")))

        shouldThrow<IllegalStateException> { l.connect().collect { } }

        events shouldBe listOf("bind") // bound never became true -> no unbind
        events shouldNotContain "unbind"
    }

    @Test fun `a bind that never connects fails with AdbException after the timeout`() = runTest {
        // Upstream Shizuku defect: bindUserService() returns fine but onServiceConnected never fires.
        val l = launcher(FakeFactory(FakeService()))

        val caught = CompletableDeferred<Throwable>()
        val job = launch {
            try {
                l.connect().collect { }
            } catch (e: Throwable) {
                caught.complete(e)
            }
        }
        advanceUntilIdle() // past the connect deadline

        val error = caught.await()
        error.shouldBeInstanceOf<AdbException>()
        error.message!! shouldContain "did not connect"
        events shouldContainInOrder listOf("bind", "unbind", "awaitDisconnect") // teardown still ran
        job.cancelAndJoin()
    }

    @Test fun `a failing handshake closes the flow bounded`() = runTest {
        val factory = FakeFactory(FakeService(), handshakeError = IllegalStateException("handshake boom"))
        val l = launcher(factory)

        val caught = CompletableDeferred<Throwable>()
        val job = launch {
            try {
                l.connect().collect { }
            } catch (e: Throwable) {
                caught.complete(e)
            }
        }
        runCurrent() // without this the callback isn't captured yet and this degrades to a timeout test

        factory.connectedCallback.shouldNotBeNull().invoke(mockk<IBinder>())
        advanceUntilIdle()

        val error = caught.await()
        error.shouldBeInstanceOf<AdbException>()
        error.message!! shouldContain "handshake failed"
        events shouldContainInOrder listOf("bind", "handshake", "unbind")
        job.cancelAndJoin()
    }

    @Test fun `a bind wedged in its binder transaction still releases collectors after the timeout`() = runTest(
        timeout = 10.seconds,
    ) {
        // Upstream Shizuku defect, second variant: bindUserService() itself never returns (wedged
        // synchronous binder transaction). The blocked thread can't be interrupted, but the
        // watchdog's close() must still release everyone waiting on this flow.
        val bindEntered = CompletableDeferred<Unit>()
        val bindWedge = CountDownLatch(1)
        val unboundLate = CompletableDeferred<Unit>()
        val service = object : ShizukuUserService {
            override fun bind() {
                bindEntered.complete(Unit)
                bindWedge.await() // blocks the calling thread, unaffected by coroutine cancellation
            }

            override fun unbind() {
                unboundLate.complete(Unit)
            }

            override suspend fun awaitDisconnect() {}
        }
        // Real scope + real IO dispatcher: the wedge blocks an actual thread, virtual time and
        // Unconfined execution can't model it (Unconfined would block the producer's own thread).
        val realScope = CoroutineScope(SupervisorJob())
        val l = AdbHostLauncher(
            serviceFactory = FakeFactory(service),
            appScope = realScope,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
        )

        try {
            // Collection runs in its own scope: on a regression the collector blocks forever, and
            // it must do so in a coroutine the test only awaits WITH a timeout - a blocked child
            // of the test coroutine itself would defeat runTest's timeout (non-cooperative
            // cancellation) and hang the JVM.
            val collectResult = realScope.async(Dispatchers.Default) {
                runCatching { l.connect(connectTimeoutMs = 250L).collect { } }
            }

            withContext(Dispatchers.Default) {
                // Only measure once the wedge is real: bind() has been entered and is blocked.
                awaitOrFail("bind()") { bindEntered.await() }

                val error = awaitOrFail("collector") { collectResult.await() }.exceptionOrNull()
                error.shouldBeInstanceOf<AdbException>()
                error.message!! shouldContain "did not connect"

                // While bind() is still wedged there is nothing to unbind yet.
                unboundLate.isCompleted shouldBe false

                // When the wedged transaction finally returns, the binding must not leak:
                // teardown already gave up, so the late unbind is the only cleanup left.
                bindWedge.countDown()
                awaitOrFail("late unbind") { unboundLate.await() }
            }
        } finally {
            bindWedge.countDown()
            realScope.cancel()
        }
    }

    @Test fun `a getVersion wedged in its binder transaction fails instead of hanging`() = runTest(
        timeout = 10.seconds,
    ) {
        // Shizuku.getVersion() only returns a cached field once the server has pushed its version via
        // bindApplication(); until then it is a synchronous binder transaction. It runs before the
        // connect watchdog is armed, so without its own bound a wedge here hangs every collector -
        // the same eternal setup spinner, just one step earlier than the bind wedge above.
        val versionEntered = CompletableDeferred<Unit>()
        val versionWedge = CountDownLatch(1)
        // Real scope + real IO dispatcher: the wedge blocks an actual thread, virtual time and
        // Unconfined execution can't model it (Unconfined would block the producer's own thread).
        val realScope = CoroutineScope(SupervisorJob())
        val l = AdbHostLauncher(
            serviceFactory = FakeFactory(versionWedge = versionWedge, versionEntered = versionEntered),
            appScope = realScope,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
        )

        try {
            val collectResult = realScope.async(Dispatchers.Default) {
                runCatching { l.connect(apiVersionTimeoutMs = 250L).collect { } }
            }

            withContext(Dispatchers.Default) {
                // Only measure once the wedge is real: apiVersion() has been entered and is blocked.
                awaitOrFail("apiVersion()") { versionEntered.await() }

                val error = awaitOrFail("collector") { collectResult.await() }.exceptionOrNull()
                error.shouldBeInstanceOf<AdbConnectTimeoutException>()
                error.message!! shouldContain "getVersion"

                // Bailed out before binding, so there is nothing to unbind.
                events.shouldBeEmpty()
            }
        } finally {
            versionWedge.countDown()
            realScope.cancel()
        }
    }

    @Test fun `watchdog does not fire after a successful connect`() = runTest {
        val factory = FakeFactory(FakeService())
        val l = launcher(factory)

        val emitted = mutableListOf<AdbHostLauncher.ConnectionWrapper<AdbConnection, AdbConnection>>()
        val caught = CompletableDeferred<Throwable>()
        val job = launch {
            try {
                l.connect().collect { emitted += it }
            } catch (e: Throwable) {
                caught.complete(e)
            }
        }
        runCurrent()

        factory.connectedCallback.shouldNotBeNull().invoke(mockk<IBinder>())
        runCurrent()
        emitted shouldHaveSize 1

        advanceTimeBy(60 * 1000L) // way past the connect deadline
        advanceUntilIdle()

        caught.isCompleted shouldBe false // still connected, nothing was torn down
        emitted shouldHaveSize 1
        job.cancelAndJoin()
    }

    @Test fun `an unbind wedged in its binder transaction still releases collectors`() = runTest(
        timeout = 10.seconds,
    ) {
        // Third variant of the same upstream wedge, on the teardown side: bind() returns, the service
        // never calls back, the watchdog close()s - and then unbindUserService() wedges against the
        // same unresponsive server. Collection of a callbackFlow awaits its producer, so before this
        // was bounded the close() never reached anyone: collectors waited on a flow whose producer sat
        // in an uninterruptible binder call inside NonCancellable, forever.
        val unbindEntered = CompletableDeferred<Unit>()
        val unbindWedge = CountDownLatch(1)
        val service = object : ShizukuUserService {
            override fun bind() {} // returns cleanly, so teardown owns the unbind (BindState.BOUND)

            override fun unbind() {
                unbindEntered.complete(Unit)
                unbindWedge.await() // blocks the calling thread, unaffected by coroutine cancellation
            }

            override suspend fun awaitDisconnect() {}
        }
        // Real scope + real IO dispatcher: the wedge blocks an actual thread, virtual time and
        // Unconfined execution can't model it (Unconfined would block the producer's own thread).
        val realScope = CoroutineScope(SupervisorJob())
        val l = AdbHostLauncher(
            serviceFactory = FakeFactory(service),
            appScope = realScope,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
        )

        try {
            // Collection runs in its own scope: on a regression the collector blocks forever, and it
            // must do so in a coroutine the test only awaits WITH a timeout - a blocked child of the
            // test coroutine itself would defeat runTest's timeout and hang the JVM.
            val collectResult = realScope.async(Dispatchers.Default) {
                runCatching { l.connect(connectTimeoutMs = 250L, unbindTimeoutMs = 250L).collect { } }
            }

            withContext(Dispatchers.Default) {
                // Only measure once the wedge is real: unbind() has been entered and is blocked.
                awaitOrFail("unbind()") { unbindEntered.await() }

                val error = awaitOrFail("collector") { collectResult.await() }.exceptionOrNull()
                error.shouldBeInstanceOf<AdbConnectTimeoutException>()
                error.message!! shouldContain "did not connect"
            }
        } finally {
            unbindWedge.countDown()
            realScope.cancel()
        }
    }


    @Test fun `a dead app scope during teardown does not abandon the rest of it`() = runTest(
        timeout = 10.seconds,
    ) {
        // The detached unbind runs on @AppScope. If that scope is already cancelled, async() yields a
        // cancelled Deferred and await() throws a CancellationException that withTimeoutOrNull does
        // NOT convert (it only converts its own timeout). What the collector sees is safe either way
        // - the watchdog's close() latched the cause before teardown ran - so this asserts the part
        // that is NOT safe: an escaping throw abandons the rest of teardown. The disconnect wait
        // after the unbind is the observable half of that.
        val bindReturned = CompletableDeferred<Unit>()
        val disconnectAwaited = CompletableDeferred<Unit>()
        val service = object : ShizukuUserService {
            override fun bind() {
                bindReturned.complete(Unit)
            }

            // Never reached: the dead scope means the detached block never runs.
            override fun unbind() = error("must not be reached: the app scope is dead")

            override suspend fun awaitDisconnect() {
                disconnectAwaited.complete(Unit)
            }
        }
        val appScope = CoroutineScope(SupervisorJob())
        val collectScope = CoroutineScope(SupervisorJob())
        val l = AdbHostLauncher(
            serviceFactory = FakeFactory(service),
            appScope = appScope,
            dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
        )

        try {
            // Watchdog long enough that appScope is reliably dead before teardown starts.
            val collectResult = collectScope.async(Dispatchers.Default) {
                runCatching { l.connect(connectTimeoutMs = 1_500L).collect { } }
            }

            withContext(Dispatchers.Default) {
                awaitOrFail("bind()") { bindReturned.await() }
                // bind() has returned, so the bind job's CAS to BOUND (which runs immediately after)
                // has effectively landed - that is the branch that reaches the unbind at teardown.
                // Settle before killing the scope so this doesn't test the TEARDOWN branch instead.
                delay(200)
                appScope.cancel()

                val error = awaitOrFail("collector") { collectResult.await() }.exceptionOrNull()
                error.shouldBeInstanceOf<AdbConnectTimeoutException>()
                error.message!! shouldContain "did not connect"

                // The load-bearing assertion: teardown continued past the unbind it could not start.
                // This also rules out a vacuous pass via the TEARDOWN branch, which never gets here.
                withClue("teardown was abandoned when the detached unbind could not run") {
                    disconnectAwaited.isCompleted shouldBe true
                }
            }
        } finally {
            appScope.cancel()
            collectScope.cancel()
        }
    }

}
