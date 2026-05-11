package eu.darken.sdmse.common.shell.ipc

import android.os.IBinder
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.ServiceConnectionLostException
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ShellOpsClientStreamingTest : BaseTest() {

    private fun makeScope() = CoroutineScope(Job() + Dispatchers.IO)

    /** Stub that pretends to be an AIDL ShellOpsConnection, returning a pre-built RemoteInputStream. */
    private class FakeConnection(
        private val streamFactory: () -> RemoteInputStream,
    ) : ShellOpsConnection {
        override fun execute(cmd: ShellOpsCmd?): ShellOpsResult =
            throw UnsupportedOperationException("not used in streaming tests")

        override fun executeStream(cmd: ShellOpsCmd?): RemoteInputStream = streamFactory()

        override fun asBinder(): IBinder? = null
    }

    @Test
    fun `executeStream forwards normal events as a Flow`() {
        val scope = makeScope()
        try {
            runBlocking {
                val events = listOf(
                    ShellOpsStreamEvent.Stdout("hello"),
                    ShellOpsStreamEvent.Stdout("world"),
                    ShellOpsStreamEvent.Stderr("warn!"),
                    ShellOpsStreamEvent.Exit(0),
                )
                val client = ShellOpsClient(
                    connection = FakeConnection { events.asFlow().toRemoteInputStream(scope) },
                    dispatcherProvider = TestDispatcherProvider(),
                )

                val collected = client.executeStream(ShellOpsCmd("ignored")).toList()
                collected shouldContainExactly events
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `executeStream unwraps IOException from wrapped Error event`() {
        // The host's IpcHostModule.wrapToPropagate() formats the exception as:
        //     "<exception-class>: <message>"
        // The client must round-trip that back to the original exception type via
        // IpcClientModule.refineException -> unwrapPropagation. Otherwise callers catching
        // specific exception types (e.g. RootHostLauncher catching IOException) silently
        // start seeing generic Exception and miss the handler.
        val wrappedMessage = "java.io.IOException: Stream closed"
        val scope = makeScope()
        try {
            runBlocking {
                val client = ShellOpsClient(
                    connection = FakeConnection {
                        flow<ShellOpsStreamEvent> {
                            emit(ShellOpsStreamEvent.Stdout("partial output"))
                            emit(ShellOpsStreamEvent.Error(wrappedMessage))
                        }.toRemoteInputStream(scope)
                    },
                    dispatcherProvider = TestDispatcherProvider(),
                )

                val thrown = runCatching { client.executeStream(ShellOpsCmd("ignored")).toList() }
                    .exceptionOrNull()
                thrown.shouldBeInstanceOf<IOException>()
                thrown!!.message shouldBe "Stream closed"
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `executeStream surfaces ServiceConnectionLostException via DeadObject path`() {
        // When the AIDL call itself raises DeadObjectException (binder went away before
        // we even got a RemoteInputStream), refineException must promote it to
        // ServiceConnectionLostException. This is the path that drives the "Service
        // Connection Lost" dialog — without this mapping, the dashboard would treat the
        // failure as a generic crash.
        val scope = makeScope()
        try {
            runBlocking {
                val client = ShellOpsClient(
                    connection = object : ShellOpsConnection {
                        override fun execute(cmd: ShellOpsCmd?): ShellOpsResult =
                            throw UnsupportedOperationException()
                        override fun executeStream(cmd: ShellOpsCmd?): RemoteInputStream =
                            throw android.os.DeadObjectException("binder died")
                        override fun asBinder(): IBinder? = null
                    },
                    dispatcherProvider = TestDispatcherProvider(),
                )

                val thrown = runCatching { client.executeStream(ShellOpsCmd("ignored")).toList() }
                    .exceptionOrNull()
                thrown.shouldBeInstanceOf<ServiceConnectionLostException>()
            }
        } finally {
            scope.cancel()
        }
    }

}
