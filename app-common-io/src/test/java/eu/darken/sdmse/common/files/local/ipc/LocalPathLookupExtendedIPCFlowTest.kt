package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class LocalPathLookupExtendedIPCFlowTest : BaseTest() {

    private fun makeScope() = CoroutineScope(Job() + Dispatchers.IO)

    private fun lookup(name: String, size: Long = 16) = LocalPathLookupExtended(
        lookup = LocalPathLookup(
            lookedUp = LocalPath.build("test", name),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        ownership = null,
        permissions = null,
    )

    @Test
    fun `round trip preserves all items and order`() {
        val items = listOf(lookup("file1"), lookup("file2", size = 8), lookup("file3", size = 0))
        val scope = makeScope()
        try {
            runBlocking {
                val collected = items.asFlow().toRemoteInputStream(scope).toLocalPathLookupExtendedFlow().toList()
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
                val collected = emptyList<LocalPathLookupExtended>().asFlow()
                    .toRemoteInputStream(scope).toLocalPathLookupExtendedFlow().toList()
                collected shouldBe emptyList()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `large directory streams across multiple chunks`() {
        val itemCount = 5000
        val source: Flow<LocalPathLookupExtended> = flow {
            repeat(itemCount) { emit(lookup("file$it")) }
        }
        val scope = makeScope()
        try {
            runBlocking {
                val collected = source.toRemoteInputStream(scope).toLocalPathLookupExtendedFlow().toList()
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
        val parcel = Parcel.obtain().apply {
            LocalPathLookupExtendedResultsIPCWrapper(
                listOf(LocalPathLookupExtendedResult.Success(lookup("file1"))),
            ).writeToParcel(this, 0)
        }
        val encoded = parcel.marshall().toByteString().base64()
        parcel.recycle()
        val bytes = (encoded + "\n").toByteArray()

        runBlocking {
            val thrown = runCatching {
                ByteArrayInputStream(bytes).remoteInputStream().toLocalPathLookupExtendedFlow().toList()
            }.exceptionOrNull()
            thrown.shouldBeInstanceOf<IOException>()
            (thrown.message ?: "") shouldBe "LocalPathLookupExtended stream ended without terminal event"
        }
    }

    @Test
    fun `source flow error propagates with class and message`() {
        val source: Flow<LocalPathLookupExtended> = flow {
            emit(lookup("file1"))
            throw IllegalStateException("extended lookup blew up")
        }
        val scope = makeScope()
        try {
            runBlocking {
                val thrown = runCatching {
                    source.toRemoteInputStream(scope).toLocalPathLookupExtendedFlow().toList()
                }.exceptionOrNull()
                thrown.shouldBeInstanceOf<IllegalStateException>()
                thrown.message shouldBe "extended lookup blew up"
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `data after Complete is ignored`() {
        fun frame(vararg results: LocalPathLookupExtendedResult): ByteArray {
            val parcel = Parcel.obtain().apply {
                LocalPathLookupExtendedResultsIPCWrapper(results.toList()).writeToParcel(this, 0)
            }
            val encoded = parcel.marshall().toByteString().base64()
            parcel.recycle()
            return (encoded + "\n").toByteArray()
        }
        val bytes = frame(LocalPathLookupExtendedResult.Success(lookup("before")), LocalPathLookupExtendedResult.Complete) +
            frame(LocalPathLookupExtendedResult.Success(lookup("after")))

        runBlocking {
            val collected = ByteArrayInputStream(bytes).remoteInputStream()
                .toLocalPathLookupExtendedFlow().toList()
            collected shouldContainExactly listOf(lookup("before"))
        }
    }

    @Test
    fun `data after Complete in the same chunk is ignored`() {
        val parcel = Parcel.obtain().apply {
            LocalPathLookupExtendedResultsIPCWrapper(
                listOf(
                    LocalPathLookupExtendedResult.Success(lookup("before")),
                    LocalPathLookupExtendedResult.Complete,
                    LocalPathLookupExtendedResult.Success(lookup("after")),
                ),
            ).writeToParcel(this, 0)
        }
        val encoded = parcel.marshall().toByteString().base64()
        parcel.recycle()
        val bytes = (encoded + "\n").toByteArray()

        runBlocking {
            val collected = ByteArrayInputStream(bytes).remoteInputStream()
                .toLocalPathLookupExtendedFlow().toList()
            collected shouldContainExactly listOf(lookup("before"))
        }
    }
}
