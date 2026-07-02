package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.ipc.remoteInputStream
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineExceptionHandler
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
import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class LocalPathLookupIPCFlowTest : BaseTest() {

    private fun makeScope() = CoroutineScope(Job() + Dispatchers.IO)

    private fun lookup(name: String, size: Long = 16) = LocalPathLookup(
        lookedUp = LocalPath.build("test", name),
        fileType = FileType.FILE,
        size = size,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    @Test
    fun `round trip preserves all items and order`() {
        val items = listOf(
            lookup("file1"),
            lookup("file2", size = 8),
            lookup("file3", size = 0),
        )
        val scope = makeScope()
        try {
            runBlocking {
                val collected = items.asFlow().toRemoteInputStream(scope).toLocalPathLookupFlow().toList()
                collected shouldContainExactly items
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `empty directory round-trips to empty list`() {
        val scope = makeScope()
        try {
            runBlocking {
                val collected = emptyList<LocalPathLookup>().asFlow()
                    .toRemoteInputStream(scope).toLocalPathLookupFlow().toList()
                collected shouldBe emptyList()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `large directory streams across multiple chunks`() {
        // This is the regression guard: the old path materialized the whole directory into a
        // single parcel + buffer copy on the memory-constrained privileged host, which could OOM
        // the helper. The chunked transport must deliver every item without doing that.
        val itemCount = 5000
        val source: Flow<LocalPathLookup> = flow {
            repeat(itemCount) { emit(lookup("file$it")) }
        }
        val scope = makeScope()
        try {
            runBlocking {
                val collected = source.toRemoteInputStream(scope).toLocalPathLookupFlow().toList()
                collected.size shouldBe itemCount
                collected.first() shouldBe lookup("file0")
                collected.last() shouldBe lookup("file${itemCount - 1}")
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `truncated stream without terminal marker fails`() {
        // Simulate the host dying mid-stream: a valid Success chunk followed by EOF and no
        // Complete marker. The consumer must reject this instead of silently returning a partial
        // listing — this is the regression the terminal marker guards against.
        val parcel = Parcel.obtain().apply {
            LocalPathLookupResultsIPCWrapper(
                listOf(LocalPathLookupResult.Success(lookup("file1"))),
            ).writeToParcel(this, 0)
        }
        val encoded = parcel.marshall().toByteString().base64()
        parcel.recycle()
        val bytes = (encoded + "\n").toByteArray()

        runBlocking {
            val thrown = runCatching {
                ByteArrayInputStream(bytes).remoteInputStream().toLocalPathLookupFlow().toList()
            }.exceptionOrNull()
            thrown.shouldBeInstanceOf<IOException>()
            (thrown.message ?: "") shouldBe "LocalPathLookup stream ended without terminal event"
        }
    }

    @Test
    fun `source flow error propagates to the consumer with class and message`() {
        val source: Flow<LocalPathLookup> = flow {
            emit(lookup("file1"))
            throw IllegalStateException("listFiles blew up")
        }
        val scope = makeScope()
        try {
            runBlocking {
                val thrown = runCatching {
                    source.toRemoteInputStream(scope).toLocalPathLookupFlow().toList()
                }.exceptionOrNull()
                thrown.shouldBeInstanceOf<IllegalStateException>()
                thrown.message shouldBe "listFiles blew up"
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `source cancellation is not fabricated into an Error frame`() {
        // Regression guard: the host's catch must rethrow CancellationException rather than encode
        // it as an Error result. The consumer then sees a truncated stream (IOException), never a
        // reconstructed CancellationException delivered as data.
        val source: Flow<LocalPathLookup> = flow {
            emit(lookup("file1"))
            throw kotlinx.coroutines.CancellationException("cancelled")
        }
        val scope = makeScope()
        try {
            runBlocking {
                val thrown = runCatching {
                    source.toRemoteInputStream(scope).toLocalPathLookupFlow().toList()
                }.exceptionOrNull()
                thrown.shouldBeInstanceOf<IOException>()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `consumer cancellation does not propagate into the host scope`() {
        // Regression guard: the client decoder closes the pipe when its collector is cancelled
        // (take(), user-cancelled scan). The host writer then fails with "Pipe closed" — that
        // failure must be contained, not rethrown into `scope`: in production that is the
        // helper's unsupervised app scope, where an uncaught exception kills the privileged
        // process (surfacing as ServiceConnectionLost on the next IPC call).
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val supervisor = SupervisorJob()
        val scope = CoroutineScope(
            supervisor + Dispatchers.IO + CoroutineExceptionHandler { _, e -> uncaught += e },
        )
        try {
            runBlocking {
                val source: Flow<LocalPathLookup> = flow {
                    repeat(100_000) { emit(lookup("file$it")) }
                }
                val collected = source.toRemoteInputStream(scope).toLocalPathLookupFlow().take(3).toList()
                collected.size shouldBe 3
                // The writer must unwind on the broken pipe instead of hanging or throwing.
                withTimeout(10_000) { supervisor.children.toList().joinAll() }
                uncaught shouldBe emptyList<Throwable>()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `invalid base64 frame fails`() {
        val bytes = "***not-valid-base64***\n".toByteArray()
        runBlocking {
            val thrown = runCatching {
                ByteArrayInputStream(bytes).remoteInputStream().toLocalPathLookupFlow().toList()
            }.exceptionOrNull()
            thrown.shouldBeInstanceOf<IOException>()
        }
    }

    @Test
    fun `data after Complete is ignored`() {
        fun frame(vararg results: LocalPathLookupResult): ByteArray {
            val parcel = Parcel.obtain().apply {
                LocalPathLookupResultsIPCWrapper(results.toList()).writeToParcel(this, 0)
            }
            val encoded = parcel.marshall().toByteString().base64()
            parcel.recycle()
            return (encoded + "\n").toByteArray()
        }
        val bytes = frame(LocalPathLookupResult.Success(lookup("before")), LocalPathLookupResult.Complete) +
            frame(LocalPathLookupResult.Success(lookup("after")))

        runBlocking {
            val collected = ByteArrayInputStream(bytes).remoteInputStream().toLocalPathLookupFlow().toList()
            collected shouldContainExactly listOf(lookup("before"))
        }
    }

    @Test
    fun `data after Complete in the same chunk is ignored`() {
        val parcel = Parcel.obtain().apply {
            LocalPathLookupResultsIPCWrapper(
                listOf(
                    LocalPathLookupResult.Success(lookup("before")),
                    LocalPathLookupResult.Complete,
                    LocalPathLookupResult.Success(lookup("after")),
                ),
            ).writeToParcel(this, 0)
        }
        val encoded = parcel.marshall().toByteString().base64()
        parcel.recycle()
        val bytes = (encoded + "\n").toByteArray()

        runBlocking {
            val collected = ByteArrayInputStream(bytes).remoteInputStream().toLocalPathLookupFlow().toList()
            collected shouldContainExactly listOf(lookup("before"))
        }
    }
}
