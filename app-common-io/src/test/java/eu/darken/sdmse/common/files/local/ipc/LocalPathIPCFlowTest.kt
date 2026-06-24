package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.ipc.remoteInputStream
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.ByteArrayInputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class LocalPathIPCFlowTest : BaseTest() {

    private fun makeScope() = CoroutineScope(Job() + Dispatchers.IO)

    private fun path(name: String) = LocalPath.build("test", name)

    @Test
    fun `round trip preserves all items and order`() {
        val items = listOf(path("file1"), path("file2"), path("file3"))
        val scope = makeScope()
        try {
            runBlocking {
                val collected = items.asFlow().toRemoteInputStream(scope).toLocalPathFlow().toList()
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
                val collected = emptyList<LocalPath>().asFlow()
                    .toRemoteInputStream(scope).toLocalPathFlow().toList()
                collected shouldBe emptyList()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `large directory streams across multiple chunks`() {
        val itemCount = 5000
        val source: Flow<LocalPath> = flow {
            repeat(itemCount) { emit(path("file$it")) }
        }
        val scope = makeScope()
        try {
            runBlocking {
                val collected = source.toRemoteInputStream(scope).toLocalPathFlow().toList()
                collected.size shouldBe itemCount
                collected.first() shouldBe path("file0")
                collected.last() shouldBe path("file${itemCount - 1}")
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `truncated stream without terminal marker fails`() {
        val parcel = Parcel.obtain().apply {
            LocalPathResultsIPCWrapper(
                listOf(LocalPathResult.Success(path("file1"))),
            ).writeToParcel(this, 0)
        }
        val encoded = parcel.marshall().toByteString().base64()
        parcel.recycle()
        val bytes = (encoded + "\n").toByteArray()

        runBlocking {
            val thrown = runCatching {
                ByteArrayInputStream(bytes).remoteInputStream().toLocalPathFlow().toList()
            }.exceptionOrNull()
            thrown.shouldBeInstanceOf<IOException>()
            (thrown.message ?: "") shouldBe "LocalPath stream ended without terminal event"
        }
    }

    @Test
    fun `source flow error propagates with class and message`() {
        val source: Flow<LocalPath> = flow {
            emit(path("file1"))
            throw IllegalStateException("listFiles blew up")
        }
        val scope = makeScope()
        try {
            runBlocking {
                val thrown = runCatching {
                    source.toRemoteInputStream(scope).toLocalPathFlow().toList()
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
        val source: Flow<LocalPath> = flow {
            emit(path("file1"))
            throw kotlinx.coroutines.CancellationException("cancelled")
        }
        val scope = makeScope()
        try {
            runBlocking {
                val thrown = runCatching {
                    source.toRemoteInputStream(scope).toLocalPathFlow().toList()
                }.exceptionOrNull()
                thrown.shouldBeInstanceOf<IOException>()
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
                ByteArrayInputStream(bytes).remoteInputStream().toLocalPathFlow().toList()
            }.exceptionOrNull()
            thrown.shouldBeInstanceOf<IOException>()
        }
    }

    @Test
    fun `data after Complete in the same chunk is ignored`() {
        val parcel = Parcel.obtain().apply {
            LocalPathResultsIPCWrapper(
                listOf(
                    LocalPathResult.Success(path("before")),
                    LocalPathResult.Complete,
                    LocalPathResult.Success(path("after")),
                ),
            ).writeToParcel(this, 0)
        }
        val encoded = parcel.marshall().toByteString().base64()
        parcel.recycle()
        val bytes = (encoded + "\n").toByteArray()

        runBlocking {
            val collected = ByteArrayInputStream(bytes).remoteInputStream().toLocalPathFlow().toList()
            collected shouldContainExactly listOf(path("before"))
        }
    }
}
