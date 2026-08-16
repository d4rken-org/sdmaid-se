package eu.darken.sdmse.common.files.local.ipc

import android.os.DeadObjectException
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.ServiceConnectionLostException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Argument/return plumbing of the app-side [FileOpsClient]. Each test uses a fake connection that
 * overrides only the method under test, so touching any other connection method would return the
 * AIDL default instead of the expected value.
 *
 * The enumeration flows (listFiles/lookupFiles/lookupFilesExtended) have their own coverage in
 * FileOpsClientStreamingTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class FileOpsClientTest : BaseTest() {

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

    @Test
    fun `lookUp is delegated`() {
        val expected = lookup("file")
        var seen: LocalPath? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun lookUp(path: LocalPath?): LocalPathLookup {
                seen = path
                return expected
            }
        })

        client.lookUp(path("file")) shouldBe expected
        seen shouldBe path("file")
    }

    @Test
    fun `lookUpExtended is delegated`() {
        val expected = lookupExtended("file")
        var seen: LocalPath? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun lookUpExtended(path: LocalPath?): LocalPathLookupExtended {
                seen = path
                return expected
            }
        })

        client.lookUpExtended(path("file")) shouldBe expected
        seen shouldBe path("file")
    }

    @Test
    fun `du is delegated`() {
        var seen: LocalPath? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun du(path: LocalPath?): Long {
                seen = path
                return 1234L
            }
        })

        client.du(path("dir")) shouldBe 1234L
        seen shouldBe path("dir")
    }

    @Test
    fun `mkdirs is delegated`() {
        var seen: LocalPath? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun mkdirs(path: LocalPath?): Boolean {
                seen = path
                return true
            }
        })

        client.mkdirs(path("dir")) shouldBe true
        seen shouldBe path("dir")
    }

    @Test
    fun `createNewFile is delegated`() {
        var seen: LocalPath? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun createNewFile(path: LocalPath?): Boolean {
                seen = path
                return true
            }
        })

        client.createNewFile(path("file")) shouldBe true
        seen shouldBe path("file")
    }

    @Test
    fun `canRead is delegated`() {
        var seen: LocalPath? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun canRead(path: LocalPath?): Boolean {
                seen = path
                return true
            }
        })

        client.canRead(path("file")) shouldBe true
        seen shouldBe path("file")
    }

    @Test
    fun `canWrite is delegated`() {
        var seen: LocalPath? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun canWrite(path: LocalPath?): Boolean {
                seen = path
                return true
            }
        })

        client.canWrite(path("file")) shouldBe true
        seen shouldBe path("file")
    }

    @Test
    fun `exists is delegated`() {
        var seen: LocalPath? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun exists(path: LocalPath?): Boolean {
                seen = path
                return true
            }
        })

        client.exists(path("file")) shouldBe true
        seen shouldBe path("file")
    }

    @Test
    fun `delete forwards the recursive and dryRun flags`() {
        val seen = mutableListOf<Triple<LocalPath?, Boolean, Boolean>>()
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun delete(path: LocalPath?, recursive: Boolean, dryRun: Boolean): Boolean {
                seen.add(Triple(path, recursive, dryRun))
                return true
            }
        })

        client.delete(path("file"), recursive = true, dryRun = false) shouldBe true
        client.delete(path("file"), recursive = false, dryRun = true) shouldBe true

        seen shouldContainExactly listOf(
            Triple(path("file"), true, false),
            Triple(path("file"), false, true),
        )
    }

    @Test
    fun `createSymlink forwards link and target in order`() {
        var seenLink: LocalPath? = null
        var seenTarget: LocalPath? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun createSymlink(linkPath: LocalPath?, targetPath: LocalPath?): Boolean {
                seenLink = linkPath
                seenTarget = targetPath
                return true
            }
        })

        client.createSymlink(path("link"), path("target")) shouldBe true
        seenLink shouldBe path("link")
        seenTarget shouldBe path("target")
    }

    @Test
    fun `setModifiedAt converts the Instant to epoch millis`() {
        var seenPath: LocalPath? = null
        var seenStamp: Long? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun setModifiedAt(path: LocalPath?, modifiedAt: Long): Boolean {
                seenPath = path
                seenStamp = modifiedAt
                return true
            }
        })

        client.setModifiedAt(path("file"), Instant.ofEpochMilli(1_234_567_890L)) shouldBe true
        seenPath shouldBe path("file")
        seenStamp shouldBe 1_234_567_890L
    }

    @Test
    fun `setPermissions is delegated`() {
        var seenPath: LocalPath? = null
        var seenPermissions: Permissions? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun setPermissions(path: LocalPath?, permissions: Permissions?): Boolean {
                seenPath = path
                seenPermissions = permissions
                return true
            }
        })

        client.setPermissions(path("file"), Permissions(0b111_101_101)) shouldBe true
        seenPath shouldBe path("file")
        seenPermissions shouldBe Permissions(0b111_101_101)
    }

    @Test
    fun `setOwnership is delegated`() {
        var seenPath: LocalPath? = null
        var seenOwnership: Ownership? = null
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun setOwnership(path: LocalPath?, ownership: Ownership?): Boolean {
                seenPath = path
                seenOwnership = ownership
                return true
            }
        })

        client.setOwnership(path("file"), Ownership(1000L, 1001L)) shouldBe true
        seenPath shouldBe path("file")
        seenOwnership shouldBe Ownership(1000L, 1001L)
    }

    @Test
    fun `walk rejects options the host can't execute`() {
        val opened = AtomicInteger()
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun walkStream(
                path: LocalPath?,
                pathDoesNotContain: MutableList<String>?,
                followSymlinks: Boolean,
            ): RemoteInputStream {
                opened.incrementAndGet()
                throw IllegalStateException("must not be reached")
            }
        })

        // onFilter is a suspend callback, it can't cross the binder.
        shouldThrow<IllegalArgumentException> {
            client.walk(
                path("dir"),
                APathGateway.WalkOptions(onFilter = { true }),
            )
        }
        opened.get() shouldBe 0
    }

    @Test
    fun `walk forwards its options and opens the stream at call time`() {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            runBlocking {
                val lookups = listOf(lookup("file1"), lookup("file2"))
                val opened = AtomicInteger()
                var seenPath: LocalPath? = null
                var seenFilter: List<String>? = null
                var seenFollow: Boolean? = null
                val client = FileOpsClient(object : FileOpsConnection.Default() {
                    override fun walkStream(
                        path: LocalPath?,
                        pathDoesNotContain: MutableList<String>?,
                        followSymlinks: Boolean,
                    ): RemoteInputStream {
                        opened.incrementAndGet()
                        seenPath = path
                        seenFilter = pathDoesNotContain
                        seenFollow = followSymlinks
                        return lookups.asFlow().toRemoteInputStream(scope)
                    }
                })

                val flow = client.walk(
                    path("dir"),
                    APathGateway.WalkOptions(
                        pathDoesNotContain = setOf("skipme"),
                        followSymlinks = true,
                    ),
                )

                // Unlike the enumeration flows, walk() reaches through the binder before anyone
                // collects, so an unconsumed walk already holds an IPC stream.
                opened.get() shouldBe 1
                seenPath shouldBe path("dir")
                seenFilter shouldBe listOf("skipme")
                seenFollow shouldBe true

                flow.toList() shouldContainExactly lookups
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a dead binder surfaces as ServiceConnectionLostException`() {
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun exists(path: LocalPath?): Boolean = throw DeadObjectException("binder died")
        })

        shouldThrow<ServiceConnectionLostException> { client.exists(path("file")) }
    }

    @Test
    fun `a wrapped host exception is unwrapped into its original type`() {
        // What FileOpsHost sends over the binder for an IOException (IpcHostModule.wrapToPropagate).
        val client = FileOpsClient(object : FileOpsConnection.Default() {
            override fun mkdirs(path: LocalPath?): Boolean =
                throw UnsupportedOperationException("java.io.IOException: Can't create dir")
        })

        val thrown = shouldThrow<IOException> { client.mkdirs(path("dir")) }
        thrown.message shouldBe "Can't create dir"
        thrown.shouldBeInstanceOf<IOException>()
    }
}
