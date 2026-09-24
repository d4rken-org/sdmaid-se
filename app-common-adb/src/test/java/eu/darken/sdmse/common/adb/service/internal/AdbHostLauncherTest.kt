package eu.darken.sdmse.common.adb.service.internal

import android.os.IBinder
import android.os.IInterface
import eu.darken.porter.sdk.UserServiceArgs
import eu.darken.sdmse.common.adb.AdbConnectTimeoutException
import eu.darken.sdmse.common.adb.AdbException
import eu.darken.sdmse.common.adb.service.AdbHostOptions
import eu.darken.sdmse.common.adb.shizuku.AdbAvailability
import eu.darken.sdmse.common.adb.shizuku.AdbBackend
import eu.darken.sdmse.common.adb.shizuku.AdbLink
import eu.darken.sdmse.common.adb.shizuku.PorterGateway
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.coroutine.TestDispatcherProvider
import kotlin.reflect.KClass

/**
 * Unit coverage for [AdbHostLauncher.createConnection]'s orchestration and teardown, with the Porter
 * SDK replaced by fakes of [PorterGateway]/[AdbLink] and the handshake by a fake seam.
 */
class AdbHostLauncherTest {

    private val events = mutableListOf<String>()

    private fun serviceConnected(): Flow<IBinder> = flow {
        emit(mockk<IBinder>())
        awaitCancellation()
    }

    private inner class FakeLink(
        val name: String = "link",
        val service: () -> Flow<IBinder> = { serviceConnected() },
        val onStop: suspend () -> Unit = {},
    ) : AdbLink {
        override val backend: AdbBackend = AdbBackend.SHIZUKU
        override val uid: Int = 2000
        override val permission: Flow<Boolean> = flowOf(true)
        override suspend fun checkPermission(): Boolean = true
        override suspend fun requestPermission(): Boolean = true

        override fun userService(args: UserServiceArgs): Flow<IBinder> = service()
            .onStart { events += "$name:bind" }
            .onCompletion { events += "$name:unbind" }

        override suspend fun stopUserService(args: UserServiceArgs) {
            events += "$name:stop"
            onStop()
            events += "$name:stopped"
        }
    }

    private class FakeGateway(link: AdbLink? = null) : PorterGateway {
        val linkFlow = MutableStateFlow(link)
        override val link: Flow<AdbLink?> = linkFlow
        override suspend fun availability(): AdbAvailability = AdbAvailability.NotInstalled
    }

    private inner class FakeSeam(
        val handshakeError: Throwable? = null,
    ) : AdbHostLauncherSeam {
        override fun <Host : AdbConnection> userServiceArgs(
            hostClass: KClass<Host>,
            options: AdbHostOptions,
        ): UserServiceArgs = mockk()

        @Suppress("UNCHECKED_CAST")
        override fun <Service : IInterface, Host : AdbConnection> handshake(
            binder: IBinder,
            serviceClass: KClass<Service>,
            options: AdbHostOptions,
        ): Pair<Service, Host> {
            events += "handshake"
            handshakeError?.let { throw it }
            return (mockk<AdbConnection>() as Service) to (mockk<AdbConnection>() as Host)
        }
    }

    private fun launcher(gateway: PorterGateway, seam: AdbHostLauncherSeam = FakeSeam()) = AdbHostLauncher(
        gateway = gateway,
        seam = seam,
        dispatcherProvider = TestDispatcherProvider(),
    )

    private fun AdbHostLauncher.connect(
        connectTimeoutMs: Long = AdbHostLauncher.CONNECT_TIMEOUT_MS,
        stopTimeoutMs: Long = AdbHostLauncher.STOP_TIMEOUT_MS,
    ) = createConnection(
        serviceClass = AdbConnection::class,
        hostClass = AdbConnection::class,
        // Explicit values: AdbHostOptions()'s default isDebug=BuildConfigWrap.DEBUG triggers
        // BuildConfigWrap's static init, which isn't available on a plain JVM.
        options = AdbHostOptions(isDebug = false, isTrace = false, isDryRun = false, recorderPath = null),
        connectTimeoutMs = connectTimeoutMs,
        stopTimeoutMs = stopTimeoutMs,
    )

    private class Collection(
        val emitted: MutableList<AdbHostLauncher.ConnectionWrapper<AdbConnection, AdbConnection>> = mutableListOf(),
        val caught: CompletableDeferred<Throwable> = CompletableDeferred(),
    )

    private fun TestScope.startCollecting(launcher: AdbHostLauncher, connectTimeoutMs: Long = AdbHostLauncher.CONNECT_TIMEOUT_MS) =
        Collection().let { collection ->
            collection to launch {
                try {
                    launcher.connect(connectTimeoutMs = connectTimeoutMs).collect { collection.emitted += it }
                } catch (e: Throwable) {
                    collection.caught.complete(e)
                }
            }
        }

    @Test fun `waits for a late link, then emits after the handshake`() = runTest {
        val gateway = FakeGateway(link = null)
        val (collection, job) = startCollecting(launcher(gateway))

        runCurrent()
        events.shouldBeEmpty()

        // Advancing a while first: a restarting server publishes no link for a bit, that is no failure.
        advanceTimeBy(5_000L)
        gateway.linkFlow.value = FakeLink()
        runCurrent()

        events shouldContainInOrder listOf("link:bind", "handshake")
        collection.emitted shouldHaveSize 1
        collection.caught.isCompleted shouldBe false
        job.cancelAndJoin()
    }

    @Test fun `no link within the connect budget fails with AdbConnectTimeoutException`() = runTest {
        val (collection, job) = startCollecting(launcher(FakeGateway(link = null)))

        advanceUntilIdle()

        val error = collection.caught.await()
        error.shouldBeInstanceOf<AdbConnectTimeoutException>()
        error.message!! shouldContain "did not connect"
        events.shouldBeEmpty() // never bound, so nothing to stop
        job.cancelAndJoin()
    }

    @Test fun `a service that never connects fails with AdbConnectTimeoutException and is stopped`() = runTest {
        val link = FakeLink(service = { flow { awaitCancellation() } })
        val (collection, job) = startCollecting(launcher(FakeGateway(link)))

        advanceUntilIdle()

        val error = collection.caught.await()
        error.shouldBeInstanceOf<AdbConnectTimeoutException>()
        error.message!! shouldContain "did not connect"
        events shouldContainInOrder listOf("link:bind", "link:unbind", "link:stop")
        job.cancelAndJoin()
    }

    @Test fun `watchdog does not fire after a successful connect`() = runTest {
        val (collection, job) = startCollecting(launcher(FakeGateway(FakeLink())))
        runCurrent()
        collection.emitted shouldHaveSize 1

        advanceTimeBy(60 * 1000L) // way past the connect deadline
        runCurrent()

        collection.caught.isCompleted shouldBe false
        collection.emitted shouldHaveSize 1
        job.cancelAndJoin()
    }

    @Test fun `a completing userService flow closes the connection and still stops the service`() = runTest {
        val died = CompletableDeferred<Unit>()
        val link = FakeLink(service = {
            flow {
                emit(mockk<IBinder>())
                died.await()
            }
        })
        val (collection, job) = startCollecting(launcher(FakeGateway(link)))
        runCurrent()
        collection.emitted shouldHaveSize 1

        died.complete(Unit) // service died, or the link was replaced
        advanceUntilIdle()

        val error = collection.caught.await()
        error.shouldBeInstanceOf<AdbException>()
        error.message!! shouldContain "disconnected"
        events shouldContainInOrder listOf("link:bind", "handshake", "link:unbind", "link:stop")
        job.cancelAndJoin()
    }

    @Test fun `a second binder closes the connection`() = runTest {
        val binders = Channel<IBinder>(Channel.UNLIMITED)
        val link = FakeLink(service = { binders.consumeAsFlow() })
        val (collection, job) = startCollecting(launcher(FakeGateway(link)))

        binders.send(mockk())
        runCurrent()
        collection.emitted shouldHaveSize 1

        binders.send(mockk()) // server re-connected the service with a new binder
        advanceUntilIdle()

        val error = collection.caught.await()
        error.shouldBeInstanceOf<AdbException>()
        error.message!! shouldContain "reconnected"
        events.count { it == "handshake" } shouldBe 1
        events shouldContain "link:stop"
        job.cancelAndJoin()
    }

    @Test fun `a failing handshake closes the flow and stops the service`() = runTest {
        val seam = FakeSeam(handshakeError = IllegalStateException("handshake boom"))
        val (collection, job) = startCollecting(launcher(FakeGateway(FakeLink()), seam))

        advanceUntilIdle()

        val error = collection.caught.await()
        error.shouldBeInstanceOf<AdbException>()
        error.message!! shouldContain "handshake failed"
        collection.emitted.shouldBeEmpty()
        events shouldContainInOrder listOf("link:bind", "handshake", "link:unbind", "link:stop")
        job.cancelAndJoin()
    }

    @Test fun `a failing bind closes the flow with AdbException`() = runTest {
        val link = FakeLink(service = { flow { throw IllegalStateException("bind boom") } })
        val (collection, job) = startCollecting(launcher(FakeGateway(link)))

        advanceUntilIdle()

        val error = collection.caught.await()
        error.shouldBeInstanceOf<AdbException>()
        error.message!! shouldContain "bind failed"
        job.cancelAndJoin()
    }

    @Test fun `cancelling stops the service after dropping the binding`() = runTest {
        val (collection, job) = startCollecting(launcher(FakeGateway(FakeLink())))
        runCurrent()
        collection.emitted shouldHaveSize 1

        job.cancelAndJoin()

        events shouldContainInOrder listOf("link:bind", "handshake", "link:unbind", "link:stop", "link:stopped")
    }

    @Test fun `a hanging stop is bounded and releases the next generation`() = runTest {
        val gateway = FakeGateway(FakeLink(name = "gen1", onStop = { awaitCancellation() }))
        val l = launcher(gateway)

        val (_, job1) = startCollecting(l)
        runCurrent()
        job1.cancelAndJoin() // runTest advances virtual time through the bounded stop

        events shouldContain "gen1:stop"
        events shouldNotContain "gen1:stopped"

        gateway.linkFlow.value = FakeLink(name = "gen2")
        val (collection2, job2) = startCollecting(l)
        runCurrent()
        collection2.emitted shouldHaveSize 1
        events shouldContain "gen2:bind"
        job2.cancelAndJoin()
    }

    @Test fun `a failing stop is best-effort and releases the next generation`() = runTest {
        val gateway = FakeGateway(FakeLink(name = "gen1", onStop = { throw IllegalStateException("stop boom") }))
        val l = launcher(gateway)

        val (_, job1) = startCollecting(l)
        runCurrent()
        job1.cancelAndJoin() // must not throw

        events shouldContain "gen1:stop"

        gateway.linkFlow.value = FakeLink(name = "gen2")
        val (collection2, job2) = startCollecting(l)
        runCurrent()
        collection2.emitted shouldHaveSize 1
        job2.cancelAndJoin()
    }

    @Test fun `a new generation binds only after the previous generation's stop returned`() = runTest {
        // Both generations use the same service args, so a late stop from the first would destroy the
        // second one's helper. Separate launcher instances: the launcher is not a singleton.
        val stopGate = CompletableDeferred<Unit>()
        val first = launcher(FakeGateway(FakeLink(name = "gen1", onStop = { stopGate.await() })))
        val second = launcher(FakeGateway(FakeLink(name = "gen2")))

        val (collection1, job1) = startCollecting(first)
        runCurrent()
        collection1.emitted shouldHaveSize 1

        job1.cancel()
        runCurrent()
        events shouldContain "gen1:stop"

        val (collection2, job2) = startCollecting(second)
        runCurrent()
        events shouldNotContain "gen2:bind"

        stopGate.complete(Unit)
        runCurrent()

        events shouldContainInOrder listOf("gen1:stop", "gen1:stopped", "gen2:bind")
        collection2.emitted shouldHaveSize 1
        job1.join()
        job2.cancelAndJoin()
    }
}
