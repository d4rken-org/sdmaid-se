package eu.darken.sdmse.common.root.service.internal

import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.root.RootException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit coverage for [RootHostLauncher.createConnection]'s teardown/orchestration — the logic we
 * rewrote and previously only verified on real devices. The OS/root touchpoints are replaced with
 * fakes via the injectable seams (RootHostLauncherSeam.kt).
 */
class RootHostLauncherTest {

    private val events = mutableListOf<String>()

    private inner class FakeSession(
        var onExecute: suspend (FlowCmd) -> FlowCmd.Result = { ok(it) },
        var onClose: suspend () -> Unit = {},
        var onCancel: suspend () -> Unit = {},
    ) : RootSession {
        override suspend fun execute(cmd: FlowCmd): FlowCmd.Result {
            events += "execute"
            return onExecute(cmd)
        }

        override suspend fun close() {
            events += "close"
            onClose()
        }

        override suspend fun cancel() {
            events += "cancel"
            onCancel()
        }
    }

    private inner class FakeSessionFactory(
        val session: RootSession = FakeSession(),
        val openError: Throwable? = null,
    ) : RootSessionFactory {
        override suspend fun open(scope: CoroutineScope): RootSession {
            openError?.let { throw it }
            return session
        }
    }

    private inner class FakeReceiver(val onRelease: () -> Unit = {}) : RootIpcReceiver {
        /** Captured so a test can simulate the host binding (onConnect firing during execute). */
        var deliverConnect: ((RootConnection) -> Unit)? = null

        override fun connect() {
            events += "connect"
        }

        override fun release() {
            events += "release"
            onRelease()
        }
    }

    private inner class FakeReceiverFactory(val receiver: FakeReceiver = FakeReceiver()) : RootIpcReceiverFactory {
        override fun create(
            pairingCode: String,
            onConnect: (RootConnection) -> Unit,
            onDisconnect: (RootConnection) -> Unit,
        ): RootIpcReceiver = receiver.also { it.deliverConnect = onConnect }
    }

    private class FakeCommandFactory : RootLaunchCommandFactory {
        val relocations = mutableListOf<Boolean>()
        override fun <Host : BaseRootHost> create(
            hostClass: kotlin.reflect.KClass<Host>,
            pairingCode: String,
            options: RootHostOptions,
        ): RootLaunchCommand = object : RootLaunchCommand {
            override fun build(withRelocation: Boolean): FlowCmd {
                relocations += withRelocation
                return FlowCmd("launch relocate=$withRelocation")
            }
        }
    }

    private fun launcher(
        sessionFactory: RootSessionFactory,
        receiverFactory: RootIpcReceiverFactory,
        commandFactory: RootLaunchCommandFactory = FakeCommandFactory(),
    ) = RootHostLauncher(sessionFactory, receiverFactory, commandFactory)

    private fun RootHostLauncher.connect(useMountMaster: Boolean = false) = createConnection(
        serviceClass = RootConnection::class,
        hostClass = BaseRootHost::class,
        useMountMaster = useMountMaster,
        options = RootHostOptions(),
    )

    @Test fun `session open failure still releases the receiver`() = runTest {
        val l = launcher(
            sessionFactory = FakeSessionFactory(openError = IllegalStateException("no su")),
            receiverFactory = FakeReceiverFactory(),
        )

        shouldThrow<RootException> { l.connect().collect { } }

        // connect happened, the failed open still triggered release, and there was no session to close.
        events shouldBe listOf("connect", "release")
    }

    @Test fun `cancel while parked in execute runs cleanup with release before close`() = runTest {
        val session = FakeSession(onExecute = { awaitCancellation() }) // simulate host running forever
        val cmds = FakeCommandFactory()
        val l = launcher(FakeSessionFactory(session), FakeReceiverFactory(), cmds)

        val job = launch { l.connect().collect { } }
        // runCurrent, not advanceUntilIdle: the host never binds here, so advancing virtual time
        // would trip the bind-watchdog instead of testing the cancellation teardown.
        runCurrent() // reach the parked execute()
        job.cancelAndJoin()

        events shouldContainInOrder listOf("connect", "execute", "release", "close")
        events shouldNotContain "cancel" // graceful close succeeded
        cmds.relocations shouldBe listOf(false) // cancellation rethrown -> no relocation retry
    }

    @Test fun `a close that throws falls back to cancel`() = runTest {
        val session = FakeSession(
            onExecute = { awaitCancellation() },
            onClose = { throw IllegalStateException("close boom") },
        )
        val l = launcher(FakeSessionFactory(session), FakeReceiverFactory())

        val job = launch { l.connect().collect { } }
        runCurrent()
        job.cancelAndJoin()

        events shouldContainInOrder listOf("release", "close", "cancel")
    }

    @Test fun `a close that hangs is bounded and falls back to cancel`() = runTest {
        val session = FakeSession(
            onExecute = { awaitCancellation() },
            onClose = { awaitCancellation() }, // close never returns -> must time out
        )
        val l = launcher(FakeSessionFactory(session), FakeReceiverFactory())

        val job = launch { l.connect().collect { } }
        runCurrent()
        job.cancelAndJoin() // runTest advances virtual time through the close timeout

        events shouldContainInOrder listOf("release", "close", "cancel")
    }

    @Test fun `cleanup is best-effort - failing release and close still reach cancel`() = runTest {
        val session = FakeSession(
            onExecute = { awaitCancellation() },
            onClose = { throw IllegalStateException("close boom") },
            onCancel = { throw IllegalStateException("cancel boom") },
        )
        val l = launcher(
            FakeSessionFactory(session),
            FakeReceiverFactory(FakeReceiver(onRelease = { throw IllegalStateException("release boom") })),
        )

        val job = launch { l.connect().collect { } }
        runCurrent()
        job.cancelAndJoin() // must not throw despite every cleanup step throwing

        events shouldContainInOrder listOf("release", "close", "cancel")
    }

    @Test fun `relocation is retried when the first attempt does not bind, then fails with RootException`() = runTest {
        // execute returns normally but no connection is ever delivered -> connected stays false.
        val session = FakeSession(onExecute = { ok(it) })
        val cmds = FakeCommandFactory()
        val l = launcher(FakeSessionFactory(session), FakeReceiverFactory(), cmds)

        shouldThrow<RootException> { l.connect().collect { } }

        cmds.relocations shouldBe listOf(false, true) // direct exec, then relocation retry
        events shouldContainInOrder listOf("connect", "execute", "execute", "release", "close")
    }

    @Test fun `a connection delivered during the first attempt skips relocation and is emitted`() = runTest {
        val receiverFactory = FakeReceiverFactory()
        val cmds = FakeCommandFactory()
        val connection = mockk<RootConnection> { every { userConnection } returns mockk(relaxed = true) }
        // The host "binds" during the direct exec: fire the factory-routed onConnect callback.
        val session = FakeSession(onExecute = {
            receiverFactory.receiver.deliverConnect?.invoke(connection)
            ok(it)
        })
        val l = launcher(FakeSessionFactory(session), receiverFactory, cmds)

        val emitted = mutableListOf<RootHostLauncher.ConnectionWrapper<RootConnection>>()
        val job = launch { l.connect().collect { emitted += it } }
        advanceUntilIdle()
        job.cancelAndJoin()

        cmds.relocations shouldBe listOf(false) // connected -> relocation retry skipped
        emitted shouldHaveSize 1 // factory-routed callback sent the connection into the flow
    }

    @Test fun `a host that neither binds nor exits is closed by the watchdog`() = runTest {
        // The host process wedges before it ever reaches the protocol's own self-timeout, so the
        // exec never returns and no connection is delivered.
        val session = FakeSession(onExecute = { awaitCancellation() })
        val l = launcher(FakeSessionFactory(session), FakeReceiverFactory())

        val caught = CompletableDeferred<Throwable>()
        val job = launch {
            try {
                l.connect().collect { }
            } catch (e: Throwable) {
                caught.complete(e)
            }
        }
        advanceUntilIdle() // past the bind deadline

        val error = caught.await()
        error.shouldBeInstanceOf<RootException>()
        error.message!! shouldContain "did not bind within"
        events shouldContainInOrder listOf("connect", "execute", "release", "close") // teardown ran
        job.cancelAndJoin()
    }

    @Test fun `a hanging mount-master exec is bounded by the watchdog`() = runTest {
        val session = FakeSession(onExecute = { cmd ->
            if (cmd.instructions.any { it.contains("--mount-master") }) awaitCancellation() else ok(cmd)
        })
        val l = launcher(FakeSessionFactory(session), FakeReceiverFactory())

        val caught = CompletableDeferred<Throwable>()
        val job = launch {
            try {
                l.connect(useMountMaster = true).collect { }
            } catch (e: Throwable) {
                caught.complete(e)
            }
        }
        advanceUntilIdle()

        val error = caught.await()
        error.shouldBeInstanceOf<RootException>()
        error.message!! shouldContain "did not bind within"
        events shouldContainInOrder listOf("connect", "execute", "release", "close") // teardown ran
        job.cancelAndJoin()
    }

    @Test fun `a slow connect within the protocol window is not killed`() = runTest {
        // The watchdog is a backstop ABOVE the host protocol's own 30s-per-attempt self-timeout,
        // a late-but-legitimate bind must survive it.
        val receiverFactory = FakeReceiverFactory()
        val connection = mockk<RootConnection> { every { userConnection } returns mockk(relaxed = true) }
        val session = FakeSession(onExecute = {
            delay(40 * 1000L)
            receiverFactory.receiver.deliverConnect?.invoke(connection)
            ok(it)
        })
        val l = launcher(FakeSessionFactory(session), receiverFactory)

        val emitted = mutableListOf<RootHostLauncher.ConnectionWrapper<RootConnection>>()
        val caught = CompletableDeferred<Throwable>()
        val job = launch {
            try {
                l.connect().collect { emitted += it }
            } catch (e: Throwable) {
                caught.complete(e)
            }
        }
        advanceUntilIdle()

        caught.isCompleted shouldBe false
        emitted shouldHaveSize 1
        job.cancelAndJoin()
    }

    companion object {
        private fun ok(cmd: FlowCmd) = FlowCmd.Result(cmd, FlowProcess.ExitCode.OK, emptyList(), emptyList())
    }
}
