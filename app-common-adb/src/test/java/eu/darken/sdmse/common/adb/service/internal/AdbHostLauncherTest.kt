package eu.darken.sdmse.common.adb.service.internal

import android.os.IBinder
import eu.darken.sdmse.common.adb.service.AdbHostOptions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

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
    ) : ShizukuUserServiceFactory {
        override fun apiVersion(): Int = version
        override fun <Host : AdbConnection> create(
            hostClass: KClass<Host>,
            options: AdbHostOptions,
            onConnected: (IBinder?) -> Unit,
        ): ShizukuUserService = if (bindError != null) {
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

    private fun launcher(factory: ShizukuUserServiceFactory) = AdbHostLauncher(factory)

    private fun AdbHostLauncher.connect() = createConnection(
        serviceClass = AdbConnection::class,
        hostClass = AdbConnection::class,
        // Explicit values: AdbHostOptions()'s default isDebug=BuildConfigWrap.DEBUG triggers
        // BuildConfigWrap's static init, which isn't available on a plain JVM.
        options = AdbHostOptions(isDebug = false, isTrace = false, isDryRun = false, recorderPath = null),
    )

    @Test fun `unsupported shizuku version fails before binding`() = runTest {
        val l = launcher(FakeFactory(version = 9))

        shouldThrow<IllegalStateException> { l.connect().collect { } }

        events.shouldBeEmpty() // never bound
    }

    @Test fun `cancel unbinds then waits for disconnect`() = runTest {
        val l = launcher(FakeFactory(FakeService()))

        val job = launch { l.connect().collect { } }
        advanceUntilIdle() // reach awaitClose (bound)
        job.cancelAndJoin()

        events shouldContainInOrder listOf("bind", "unbind", "awaitDisconnect")
    }

    @Test fun `a failing unbind is best-effort and still awaits disconnect`() = runTest {
        val l = launcher(FakeFactory(FakeService(onUnbind = { throw IllegalStateException("unbind boom") })))

        val job = launch { l.connect().collect { } }
        advanceUntilIdle()
        job.cancelAndJoin() // must not throw

        events shouldContainInOrder listOf("bind", "unbind", "awaitDisconnect")
    }

    @Test fun `a hanging disconnect is bounded`() = runTest {
        val l = launcher(FakeFactory(FakeService(onAwait = { awaitCancellation() }))) // never disconnects

        val job = launch { l.connect().collect { } }
        advanceUntilIdle()
        job.cancelAndJoin() // runTest advances virtual time through the bounded await

        events shouldContainInOrder listOf("bind", "unbind", "awaitDisconnect")
    }

    @Test fun `a failing bind does not attempt unbind`() = runTest {
        val l = launcher(FakeFactory(bindError = IllegalStateException("bind boom")))

        shouldThrow<IllegalStateException> { l.connect().collect { } }

        events shouldBe listOf("bind") // bound never became true -> no unbind
        events shouldNotContain "unbind"
    }
}
