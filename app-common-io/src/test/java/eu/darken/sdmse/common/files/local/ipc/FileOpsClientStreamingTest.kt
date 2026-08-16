package eu.darken.sdmse.common.files.local.ipc

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.ServiceConnectionLostException
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class FileOpsClientStreamingTest : BaseTest() {

    private fun makeScope() = CoroutineScope(Job() + Dispatchers.IO)

    private fun path(name: String) = LocalPath.build("test", name)

    private fun lookup(name: String) = LocalPathLookup(
        lookedUp = path(name),
        fileType = FileType.FILE,
        size = 16L,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    private fun lookupExtended(name: String) = LocalPathLookupExtended(
        lookup = lookup(name),
        ownership = null,
        permissions = null,
    )

    /**
     * Stub that pretends to be an AIDL FileOpsConnection, counting how often each enumeration
     * stream was opened. [FileOpsConnection.Default] supplies the unused members.
     */
    private class FakeConnection(
        private val streamFactory: () -> RemoteInputStream,
    ) : FileOpsConnection.Default() {
        val opened = AtomicInteger()

        override fun listFilesStream(path: LocalPath?): RemoteInputStream = open()

        override fun lookupFilesStream(path: LocalPath?): RemoteInputStream = open()

        override fun lookupFilesExtendedStream(path: LocalPath?): RemoteInputStream = open()

        private fun open(): RemoteInputStream {
            opened.incrementAndGet()
            return streamFactory()
        }
    }

    @Test
    fun `enumeration streams are opened at collection time, not at call time`() {
        val scope = makeScope()
        try {
            runBlocking {
                val paths = listOf(path("file1"), path("file2"))
                val lookups = listOf(lookup("file1"), lookup("file2"))
                val extended = listOf(lookupExtended("file1"), lookupExtended("file2"))

                val pathConnection = FakeConnection { paths.asFlow().toRemoteInputStream(scope) }
                val lookupConnection = FakeConnection { lookups.asFlow().toRemoteInputStream(scope) }
                val extendedConnection = FakeConnection { extended.asFlow().toRemoteInputStream(scope) }

                val listFilesFlow = FileOpsClient(pathConnection).listFiles(path("dir"))
                val lookupFilesFlow = FileOpsClient(lookupConnection).lookupFiles(path("dir"))
                val lookupFilesExtendedFlow = FileOpsClient(extendedConnection).lookupFilesExtended(path("dir"))

                // Building the flow must not touch the connection, otherwise an unconsumed flow
                // would already hold an IPC stream (and the lease keeping the helper alive).
                pathConnection.opened.get() shouldBe 0
                lookupConnection.opened.get() shouldBe 0
                extendedConnection.opened.get() shouldBe 0

                listFilesFlow.toList() shouldContainExactly paths
                lookupFilesFlow.toList() shouldContainExactly lookups
                lookupFilesExtendedFlow.toList() shouldContainExactly extended

                pathConnection.opened.get() shouldBe 1
                lookupConnection.opened.get() shouldBe 1
                extendedConnection.opened.get() shouldBe 1
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cancelling mid-stream stops consuming the remaining items`() {
        val supervisor = SupervisorJob()
        val scope = CoroutineScope(supervisor + Dispatchers.IO)
        try {
            runBlocking {
                val itemCount = 100_000
                val emitted = AtomicInteger()
                val source: Flow<LocalPathLookup> = flow {
                    repeat(itemCount) {
                        emitted.incrementAndGet()
                        emit(lookup("file$it"))
                    }
                }
                val client = FileOpsClient(FakeConnection { source.toRemoteInputStream(scope) })

                val collected = client.lookupFiles(path("dir")).take(3).toList()
                collected shouldContainExactly listOf(lookup("file0"), lookup("file1"), lookup("file2"))

                // The host unwinds on the closed pipe instead of enumerating the whole directory.
                withTimeout(10_000) { supervisor.children.toList().joinAll() }
                emitted.get() shouldBeLessThan itemCount
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a dead connection surfaces as ServiceConnectionLostException at collection time`() {
        runBlocking {
            val connection = FakeConnection { throw android.os.DeadObjectException("binder died") }
            val client = FileOpsClient(connection)

            // refineException() promotes DeadObjectException, which is what drives the
            // "Service Connection Lost" handling instead of a generic crash.
            val flows = listOf(
                client.listFiles(path("dir")),
                client.lookupFiles(path("dir")),
                client.lookupFilesExtended(path("dir")),
            )

            flows.forEach { flow ->
                val thrown = runCatching { flow.toList() }.exceptionOrNull()
                thrown.shouldBeInstanceOf<ServiceConnectionLostException>()
            }
        }
    }
}
