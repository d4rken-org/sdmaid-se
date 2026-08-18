package eu.darken.sdmse.common.files.saf

import android.provider.DocumentsContract.Document
import android.system.Os
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.WriteException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import okio.buffer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SAFGatewayTest : BaseTest() {

    @After
    fun tearDown() {
        // BaseTest's cleanup is a JUnit5 @AfterAll and doesn't run here.
        unmockkAll()
        LegacyQueryShimProvider.unregisterAll()
        // A global flag, a leak from a failing dry-run test would silently disarm every other one.
        Bugs.isDryRun = false
    }

    private fun TestScope.harness(
        provider: FakeDocumentsProvider = FakeDocumentsProvider.tree(),
        granted: List<String> = emptyList(),
    ) = SafTestHarness(appScope = this, provider = provider, grantedSegments = granted)

    /** A path on a volume we hold no Uri permission for. */
    private fun ungrantedPath(vararg segments: String) =
        SAFPath.build("content://${SafTestHarness.DEFAULT_AUTHORITY}/tree/ungranted%3A", *segments)

    // ------------------------------------------------------------------------------- exists/can*

    @Test
    fun `exists reports presence`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })

        harness.gateway.exists(harness.safPath("dir", "file.txt")) shouldBe true
        harness.gateway.exists(harness.safPath("dir")) shouldBe true
        harness.gateway.exists(harness.safPath("dir", "ghost.txt")) shouldBe false
    }

    @Test
    fun `exists throws when we hold no permission`() = runTest2(autoCancel = true) {
        val harness = harness()

        // Asymmetry worth knowing: canRead/canWrite map a missing grant to false, exists raises.
        val error = shouldThrow<ReadException> { harness.gateway.exists(ungrantedPath("file.txt")) }
        error.cause.shouldBeInstanceOf<MissingUriPermissionException>()
    }

    @Test
    fun `canRead and canWrite`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("readable.txt", flags = 0)
                file("writable.txt", flags = Document.FLAG_SUPPORTS_WRITE)
            }
        )

        harness.gateway.canRead(harness.safPath("readable.txt")) shouldBe true
        harness.gateway.canRead(harness.safPath("ghost.txt")) shouldBe false

        harness.gateway.canWrite(harness.safPath("writable.txt")) shouldBe true
        harness.gateway.canWrite(harness.safPath("readable.txt")) shouldBe false
    }

    @Test
    fun `canRead and canWrite map a missing permission to false`() = runTest2(autoCancel = true) {
        val harness = harness()

        harness.gateway.canRead(ungrantedPath("file.txt")) shouldBe false
        harness.gateway.canWrite(ungrantedPath("file.txt")) shouldBe false
    }

    // ---------------------------------------------------------------------------------- lookups

    @Test
    fun `lookup a file and a directory`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                dir("dir", lastModified = 1000L)
                file("dir/file.txt", size = 512L, lastModified = 2000L)
            }
        )

        harness.gateway.lookup(harness.safPath("dir", "file.txt")).apply {
            lookedUp shouldBe harness.safPath("dir", "file.txt")
            fileType shouldBe FileType.FILE
            size shouldBe 512L
            modifiedAt shouldBe Instant.ofEpochMilli(2000L)
        }

        harness.gateway.lookup(harness.safPath("dir")).apply {
            fileType shouldBe FileType.DIRECTORY
            modifiedAt shouldBe Instant.ofEpochMilli(1000L)
        }
    }

    @Test
    fun `lookup an unreadable document`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })

        shouldThrow<ReadException> { harness.gateway.lookup(harness.safPath("dir", "ghost.txt")) }
    }

    @Test
    fun `lookupExtended wraps the lookup`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt", size = 512L) })

        harness.gateway.lookupExtended(harness.safPath("dir", "file.txt")).apply {
            lookedUp shouldBe harness.safPath("dir", "file.txt")
            fileType shouldBe FileType.FILE
            size shouldBe 512L
        }
    }

    @Test
    fun `listFiles emits the children`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/one.txt")
                dir("dir/sub")
                file("other.txt")
            }
        )

        harness.gateway.listFiles(harness.safPath("dir")).toList() shouldContainExactly listOf(
            harness.safPath("dir", "one.txt"),
            harness.safPath("dir", "sub"),
        )
    }

    @Test
    fun `lookupFiles and lookupFilesExtended emit the children`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/one.txt", size = 1L)
                dir("dir/sub")
            }
        )

        harness.gateway.lookupFiles(harness.safPath("dir")).toList().map { it.lookedUp } shouldContainExactly listOf(
            harness.safPath("dir", "one.txt"),
            harness.safPath("dir", "sub"),
        )

        harness.gateway.lookupFilesExtended(harness.safPath("dir")).toList()
            .map { it.lookedUp } shouldContainExactly listOf(
            harness.safPath("dir", "one.txt"),
            harness.safPath("dir", "sub"),
        )
    }

    @Test
    fun `lookupFiles falls back to the uri when the provider reports no display name`() =
        runTest2(autoCancel = true) {
            val harness = harness(FakeDocumentsProvider.tree { file("dir/nameless.txt", nullDisplayName = true) })

            harness.gateway.lookupFiles(harness.safPath("dir")).toList()
                .map { it.lookedUp } shouldContainExactly listOf(harness.safPath("dir", "nameless.txt"))
        }

    @Test
    fun `listing errors are refined to ReadException`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/one.txt") })
        harness.provider.failChildQueryFor["root:dir"] = RuntimeException("provider is having a bad day")

        shouldThrow<ReadException> { harness.gateway.listFiles(harness.safPath("dir")) }
        shouldThrow<ReadException> { harness.gateway.lookupFiles(harness.safPath("dir")) }
        shouldThrow<ReadException> { harness.gateway.lookupFilesExtended(harness.safPath("dir")) }
    }

    @Test
    fun `lookupFiles enumerates eagerly`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/one.txt")
                file("dir/two.txt")
            }
        )

        val flow = harness.gateway.lookupFiles(harness.safPath("dir"))

        // The child listing is a snapshot taken at call time, before anything collects.
        harness.provider.queriedChildren shouldContainExactly listOf("root:dir")
        flow.toList().size shouldBe 2
    }

    // ------------------------------------------------------------------------------------- walk

    private fun walkTree() = FakeDocumentsProvider.tree {
        dir("dirA")
        file("dirA/fileA1")
        file("dirA/sub/fileS1")
        dir("dirB")
        file("dirB/fileB1")
        file("fileRoot")
    }

    @Test
    fun `walk emits the whole tree`() = runTest2(autoCancel = true) {
        val harness = harness(walkTree())

        harness.gateway.walk(harness.safPath()).toList().map { it.lookedUp } shouldContainExactly listOf(
            harness.safPath("dirA"),
            harness.safPath("dirB"),
            harness.safPath("fileRoot"),
            harness.safPath("dirB", "fileB1"),
            harness.safPath("dirA", "fileA1"),
            harness.safPath("dirA", "sub"),
            harness.safPath("dirA", "sub", "fileS1"),
        )
    }

    @Test
    fun `walk on a file emits just that file`() = runTest2(autoCancel = true) {
        val harness = harness(walkTree())

        harness.gateway.walk(harness.safPath("fileRoot")).toList()
            .map { it.lookedUp } shouldContainExactly listOf(harness.safPath("fileRoot"))
    }

    @Test
    fun `walk skips what onFilter rejects, including its subtree`() = runTest2(autoCancel = true) {
        val harness = harness(walkTree())
        val options = APathGateway.WalkOptions<SAFPath, SAFPathLookup>(
            onFilter = { it.name != "dirA" },
        )

        harness.gateway.walk(harness.safPath(), options).toList()
            .map { it.lookedUp } shouldContainExactly listOf(
            harness.safPath("dirB"),
            harness.safPath("fileRoot"),
            harness.safPath("dirB", "fileB1"),
        )
    }

    @Test
    fun `walk continues when onError allows it`() = runTest2(autoCancel = true) {
        val harness = harness(walkTree())
        harness.provider.failChildQueryFor["root:dirA"] = RuntimeException("provider is having a bad day")
        val seenErrors = mutableListOf<SAFPathLookup>()
        val options = APathGateway.WalkOptions<SAFPath, SAFPathLookup>(
            onError = { lookup, _ ->
                seenErrors.add(lookup)
                true
            },
        )

        harness.gateway.walk(harness.safPath(), options).toList()
            .map { it.lookedUp } shouldContainExactly listOf(
            harness.safPath("dirA"),
            harness.safPath("dirB"),
            harness.safPath("fileRoot"),
            harness.safPath("dirB", "fileB1"),
        )
        seenErrors.map { it.lookedUp } shouldContainExactly listOf(harness.safPath("dirA"))
    }

    @Test
    fun `walk aborts when onError rejects`() = runTest2(autoCancel = true) {
        val harness = harness(walkTree())
        harness.provider.failChildQueryFor["root:dirA"] = RuntimeException("provider is having a bad day")
        val options = APathGateway.WalkOptions<SAFPath, SAFPathLookup>(
            onError = { _, _ -> false },
        )

        shouldThrow<ReadException> { harness.gateway.walk(harness.safPath(), options).toList() }
    }

    @Test
    fun `walk applies pathDoesNotContain`() = runTest2(autoCancel = true) {
        // Same semantic as the local gateway (FileOpsHost.walkStream): a child whose path contains
        // any of the strings is skipped, and a skipped directory is not descended into.
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("keepme/kept.txt")
                file("skipme/skipped.txt")
            }
        )
        val options = APathGateway.WalkOptions<SAFPath, SAFPathLookup>(
            pathDoesNotContain = setOf("skipme"),
        )

        harness.gateway.walk(harness.safPath(), options).toList()
            .map { it.lookedUp } shouldContainExactly listOf(
            harness.safPath("keepme"),
            harness.safPath("keepme", "kept.txt"),
        )
    }

    @Test
    fun `walk cancellation is not converted into a ReadException`() = runTest2(autoCancel = true) {
        val harness = harness(walkTree())
        val scope = CoroutineScope(Dispatchers.Unconfined)
        harness.provider.onChildQuery = { scope.cancel() }
        var caught: Throwable? = null

        val job = scope.launch {
            try {
                harness.gateway.walk(harness.safPath()).toList()
            } catch (e: Throwable) {
                caught = e
            }
        }
        job.join()

        caught.shouldBeInstanceOf<CancellationException>()
    }

    // --------------------------------------------------------------------------------------- du

    @Test
    fun `du sums the tree`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("a.txt", size = 100L)
                file("dir/b.txt", size = 200L)
                file("dir/sub/c.txt", size = 300L)
            }
        )

        harness.gateway.du(harness.safPath()) shouldBe 600L
        harness.gateway.du(harness.safPath("dir")) shouldBe 500L
        harness.gateway.du(harness.safPath("a.txt")) shouldBe 100L
    }

    @Test
    fun `du swallows enumeration errors when abortOnError is false`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("a.txt", size = 100L)
                file("dir/b.txt", size = 200L)
            }
        )
        harness.provider.failChildQueryFor["root:dir"] = RuntimeException("provider is having a bad day")

        harness.gateway.du(
            harness.safPath(),
            APathGateway.DuOptions(abortOnError = false),
        ) shouldBe 100L
    }

    @Test
    fun `du propagates enumeration errors when abortOnError is true`() = runTest2(autoCancel = true) {
        // Without this, du reports a partial total as if it were complete.
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("a.txt", size = 100L)
                file("dir/b.txt", size = 200L)
            }
        )
        harness.provider.failChildQueryFor["root:dir"] = RuntimeException("provider is having a bad day")

        shouldThrow<ReadException> {
            harness.gateway.du(
                harness.safPath(),
                APathGateway.DuOptions(abortOnError = true),
            )
        }
    }

    // ------------------------------------------------------------------------------------- file

    @Test
    fun `file opens a read handle`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("file.txt", content = "hello-saf") })

        val handle = harness.gateway.file(harness.safPath("file.txt"), readWrite = false)
        val content = handle.use { it.source().buffer().readUtf8() }

        content shouldBe "hello-saf"
        harness.provider.openedDocuments shouldContainExactly listOf("root:file.txt" to "r")
    }

    @Test
    fun `file refuses readWrite on a non-writable document`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("file.txt", content = "hello-saf", flags = 0) })

        shouldThrow<ReadException> { harness.gateway.file(harness.safPath("file.txt"), readWrite = true) }
    }

    // -------------------------------------------------------------------------------- create*

    @Test
    fun `createFile and createDir with a single segment`() = runTest2(autoCancel = true) {
        val harness = harness()

        harness.gateway.createFile(harness.safPath("new.bin"))
        harness.provider.node("new.bin")!!.mimeType shouldBe "application/octet-stream"

        harness.gateway.createDir(harness.safPath("newdir"))
        harness.provider.node("newdir")!!.mimeType shouldBe Document.MIME_TYPE_DIR
    }

    @Test
    fun `createFile and createDir refuse an existing target`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("file.txt")
                dir("dir")
            }
        )

        shouldThrow<WriteException> { harness.gateway.createFile(harness.safPath("file.txt")) }
        shouldThrow<WriteException> { harness.gateway.createDir(harness.safPath("dir")) }
    }

    @Test
    fun `createFile on the tree root itself`() = runTest2(autoCancel = true) {
        val harness = harness()

        // The tree root exists, so the "no segments" guard inside createDocumentFile is unreachable here.
        shouldThrow<WriteException> { harness.gateway.createFile(harness.safPath()) }
    }

    @Test
    fun `createFile on a tree root the provider does not know`() = runTest2(autoCancel = true) {
        val harness = harness()
        harness.provider.removeNode()

        val error = shouldThrow<WriteException> { harness.gateway.createFile(harness.safPath()) }
        error.cause.shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `createFile below an existing directory`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })

        harness.gateway.createFile(harness.safPath("dir", "new.bin"))

        harness.provider.node("dir", "new.bin")!!.mimeType shouldBe "application/octet-stream"
    }

    @Test
    fun `createFile with all ancestors present`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { dir("a/b") })

        harness.gateway.createFile(harness.safPath("a", "b", "new.bin"))

        harness.provider.hasNode("a", "b", "new.bin") shouldBe true
        harness.provider.createdDocuments shouldContainExactly listOf("root:a/b" to "new.bin")
    }

    @Test
    fun `createFile creates the missing ancestors`() = runTest2(autoCancel = true) {
        val harness = harness()

        harness.gateway.createFile(harness.safPath("a", "b", "new.bin"))

        harness.provider.node("a")!!.mimeType shouldBe Document.MIME_TYPE_DIR
        harness.provider.node("a", "b")!!.mimeType shouldBe Document.MIME_TYPE_DIR
        harness.provider.hasNode("a", "b", "new.bin") shouldBe true
        harness.provider.createdDocuments shouldContainExactly listOf(
            "root:" to "a",
            "root:a" to "b",
            "root:a/b" to "new.bin",
        )
    }

    @Test
    fun `createDir creates the missing ancestors`() = runTest2(autoCancel = true) {
        val harness = harness()

        harness.gateway.createDir(harness.safPath("a", "b", "newdir"))

        harness.provider.node("a", "b", "newdir")!!.mimeType shouldBe Document.MIME_TYPE_DIR
    }

    @Test
    fun `createDir aborts when the provider renames a missing ancestor`() = runTest2(autoCancel = true) {
        // ExternalStorageProvider sanitizes and uniquifies display names. Continuing below the folder
        // it actually created would write the target somewhere nobody asked for.
        val harness = harness()
        harness.provider.renameNextCreatedTo = "a (1)"

        shouldThrow<WriteException> { harness.gateway.createDir(harness.safPath("a", "b", "newdir")) }

        harness.provider.createdDocuments shouldContainExactly listOf("root:" to "a")
        // The renamed directory is removed again, and neither "b" nor the target exist anywhere.
        harness.provider.deletedDocuments shouldContainExactly listOf("root:a (1)")
        harness.provider.documentIds() shouldContainExactly listOf(FakeDocumentsProvider.ROOT_ID)
    }

    @Test
    fun `createFile with a file blocking an ancestor`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("a") })

        shouldThrow<WriteException> { harness.gateway.createFile(harness.safPath("a", "b", "new.bin")) }

        harness.provider.hasNode("a", "b") shouldBe false
    }

    @Test
    fun `createFile under a grant that is narrower than the volume`() = runTest2(autoCancel = true) {
        // Ancestors are resolved from the granted document, not from the volume root: looking up the
        // volume root raises MissingUriPermissionException whenever the grant is narrower than it.
        val harness = harness(
            provider = FakeDocumentsProvider.tree { dir("Android/data") },
            granted = listOf("Android", "data"),
        )

        harness.gateway.createFile(harness.safPath("Android", "data", "pkg", "cache.bin"))

        harness.provider.node("Android", "data", "pkg")!!.mimeType shouldBe Document.MIME_TYPE_DIR
        harness.provider.hasNode("Android", "data", "pkg", "cache.bin") shouldBe true
    }

    @Test
    fun `createDir on the granted tree root itself`() = runTest2(autoCancel = true) {
        // The grant's own document has no addressable parent, so there is nothing to create it in.
        val harness = harness(
            provider = FakeDocumentsProvider.tree { dir("Android") },
            granted = listOf("Android", "data"),
        )

        shouldThrow<WriteException> { harness.gateway.createDir(harness.safPath("Android", "data")) }

        harness.provider.hasNode("Android", "data") shouldBe false
    }

    // The same create paths against a provider that hands out ids carrying no path information (like
    // most non-ExternalStorageProvider providers do). They only pass if every step addresses the
    // document the provider handed back, instead of rebuilding an id from the wanted path.

    @Test
    fun `createFile creates the missing ancestors with opaque document ids`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree(FakeDocumentsProvider.IdMode.OPAQUE))

        harness.gateway.createFile(harness.safPath("a", "b", "new.bin"))

        val (idA, idB, idTarget) = harness.provider.createdDocumentIds
        harness.provider.createdDocuments shouldContainExactly listOf(
            FakeDocumentsProvider.ROOT_ID to "a",
            idA to "b",
            idB to "new.bin",
        )
        harness.provider.nodeById(idTarget)!!.apply {
            displayName shouldBe "new.bin"
            mimeType shouldBe "application/octet-stream"
            parentId shouldBe idB
        }
        harness.provider.resolve("a", "b", "new.bin")!!.documentId shouldBe idTarget
    }

    @Test
    fun `createDir creates the missing ancestors with opaque document ids`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree(FakeDocumentsProvider.IdMode.OPAQUE))

        harness.gateway.createDir(harness.safPath("a", "b", "newdir"))

        val (idA, idB, idTarget) = harness.provider.createdDocumentIds
        harness.provider.createdDocuments shouldContainExactly listOf(
            FakeDocumentsProvider.ROOT_ID to "a",
            idA to "b",
            idB to "newdir",
        )
        harness.provider.nodeById(idTarget)!!.apply {
            displayName shouldBe "newdir"
            mimeType shouldBe Document.MIME_TYPE_DIR
            parentId shouldBe idB
        }
        harness.provider.resolve("a", "b", "newdir")!!.documentId shouldBe idTarget
    }

    @Test
    fun `createFile under a narrow grant with opaque document ids`() = runTest2(autoCancel = true) {
        val harness = harness(
            provider = FakeDocumentsProvider.tree(FakeDocumentsProvider.IdMode.OPAQUE) { dir("Android/data") },
            granted = listOf("Android", "data"),
        )

        harness.gateway.createFile(harness.safPath("Android", "data", "pkg", "cache.bin"))

        val (idPkg, idTarget) = harness.provider.createdDocumentIds
        harness.provider.createdDocuments shouldContainExactly listOf(
            "root:Android/data" to "pkg",
            idPkg to "cache.bin",
        )
        harness.provider.nodeById(idPkg)!!.parentId shouldBe "root:Android/data"
        harness.provider.nodeById(idTarget)!!.apply {
            displayName shouldBe "cache.bin"
            parentId shouldBe idPkg
        }
        harness.provider.resolve("Android", "data", "pkg", "cache.bin")!!.documentId shouldBe idTarget
    }

    @Test
    fun `createDir under a narrow grant with opaque document ids`() = runTest2(autoCancel = true) {
        val harness = harness(
            provider = FakeDocumentsProvider.tree(FakeDocumentsProvider.IdMode.OPAQUE) { dir("Android/data") },
            granted = listOf("Android", "data"),
        )

        harness.gateway.createDir(harness.safPath("Android", "data", "pkg", "cache"))

        val (idPkg, idTarget) = harness.provider.createdDocumentIds
        harness.provider.createdDocuments shouldContainExactly listOf(
            "root:Android/data" to "pkg",
            idPkg to "cache",
        )
        harness.provider.nodeById(idPkg)!!.parentId shouldBe "root:Android/data"
        harness.provider.nodeById(idTarget)!!.apply {
            displayName shouldBe "cache"
            mimeType shouldBe Document.MIME_TYPE_DIR
            parentId shouldBe idPkg
        }
        harness.provider.resolve("Android", "data", "pkg", "cache")!!.documentId shouldBe idTarget
    }

    // ----------------------------------------------------------------------------------- delete

    @Test
    fun `delete a single file`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })

        harness.gateway.delete(harness.safPath("dir", "file.txt"), recursive = false)

        harness.provider.hasNode("dir", "file.txt") shouldBe false
        harness.provider.hasNode("dir") shouldBe true
    }

    @Test
    fun `recursive delete removes the directory itself`() = runTest2(autoCancel = true) {
        // A cascading provider takes the whole subtree from a single call on the target.
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/file1.txt")
                file("dir/sub/file2.txt")
            }
        )
        harness.provider.dirDeleteMode = FakeDocumentsProvider.DirDeleteMode.CASCADE

        harness.gateway.delete(harness.safPath("dir"), recursive = true)

        harness.provider.hasNode("dir") shouldBe false
        harness.provider.hasNode("dir", "sub") shouldBe false
        harness.provider.hasNode("dir", "file1.txt") shouldBe false
        harness.provider.hasNode("dir", "sub", "file2.txt") shouldBe false
        harness.provider.deleteCalls shouldContainExactly listOf("root:dir")
    }

    @Test
    fun `recursive delete falls back to a post order walk when the provider does not cascade`() =
        runTest2(autoCancel = true) {
            val harness = harness(
                FakeDocumentsProvider.tree {
                    file("dir/file1.txt")
                    file("dir/sub/file2.txt")
                }
            )
            harness.provider.dirDeleteMode = FakeDocumentsProvider.DirDeleteMode.REJECT_NONEMPTY

            harness.gateway.delete(harness.safPath("dir"), recursive = true)

            harness.provider.hasNode("dir") shouldBe false
            harness.provider.hasNode("dir", "sub") shouldBe false
            // Children before their parents, otherwise a provider that refuses a non-empty directory
            // could never be satisfied.
            harness.provider.deletedDocuments shouldContainExactly listOf(
                "root:dir/file1.txt",
                "root:dir/sub/file2.txt",
                "root:dir/sub",
                "root:dir",
            )
        }

    @Test
    fun `recursive delete resumes when the provider cascade fails part way`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/file1.txt")
                file("dir/sub/file2.txt")
            }
        )
        harness.provider.dirDeleteMode = FakeDocumentsProvider.DirDeleteMode.CASCADE_PARTIAL_FAILURE

        harness.gateway.delete(harness.safPath("dir"), recursive = true)

        harness.provider.hasNode("dir") shouldBe false
        // The cascade took the files before it gave up, the walk finished what was left.
        harness.provider.deletedDocuments shouldContainExactly listOf(
            "root:dir/sub",
            "root:dir",
        )
    }

    @Test
    fun `delete refuses a populated directory when recursive is false`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/file1.txt")
                file("dir/sub/file2.txt")
            }
        )

        shouldThrow<WriteException> { harness.gateway.delete(harness.safPath("dir"), recursive = false) }

        harness.provider.hasNode("dir") shouldBe true
        harness.provider.hasNode("dir", "sub") shouldBe true
        harness.provider.hasNode("dir", "file1.txt") shouldBe true
        harness.provider.hasNode("dir", "sub", "file2.txt") shouldBe true
        harness.provider.deleteCalls.shouldBeEmpty()
    }

    @Test
    fun `delete removes an empty directory when recursive is false`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })

        harness.gateway.delete(harness.safPath("dir"), recursive = false)

        harness.provider.hasNode("dir") shouldBe false
        harness.provider.deletedDocuments shouldContainExactly listOf("root:dir")
    }

    @Test
    fun `a child appearing after the emptiness check is taken along`() = runTest2(autoCancel = true) {
        // The refusal in the recursive=false path is best-effort, exactly as the APathGateway.delete
        // contract states: emptiness check and delete can't be one atomic step, so a child that shows
        // up in between is cascaded away by the provider.
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })
        harness.provider.onChildQuery = {
            harness.provider.onChildQuery = null
            harness.provider.addNode(
                FakeDocumentsProvider.Node(
                    documentId = "root:dir/sneaky.txt",
                    parentId = "root:dir",
                    displayName = "sneaky.txt",
                    mimeType = "application/octet-stream",
                    flags = FakeDocumentsProvider.DEFAULT_FILE_FLAGS,
                )
            )
        }

        harness.gateway.delete(harness.safPath("dir"), recursive = false)

        harness.provider.hasNode("dir") shouldBe false
        harness.provider.hasNode("dir", "sneaky.txt") shouldBe false
    }

    @Test
    fun `delete counts a false return as success when the document is really gone`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        // Deleted, but reported as failed.
        harness.provider.failNextDeleteAfterwardsWith = IllegalArgumentException("java.io.FileNotFoundException: gone")

        harness.gateway.delete(harness.safPath("dir", "file.txt"), recursive = false)

        harness.provider.hasNode("dir", "file.txt") shouldBe false
    }

    @Test
    fun `delete surfaces a false return as WriteException when the document survives`() =
        runTest2(autoCancel = true) {
            val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
            harness.provider.failNextDeleteWith = IllegalArgumentException("java.io.FileNotFoundException: gone")

            shouldThrow<WriteException> {
                harness.gateway.delete(harness.safPath("dir", "file.txt"), recursive = false)
            }

            harness.provider.hasNode("dir", "file.txt") shouldBe true
        }

    @Test
    fun `delete surfaces a failing verification query as WriteException`() = runTest2(autoCancel = true) {
        // The lenient SAFDocFile.exists reads a failing query as "gone" and would report success for a
        // document that is still there, which is why the verification uses existsStrict.
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        harness.provider.failNextDeleteWith = IllegalArgumentException("java.io.FileNotFoundException: gone")
        harness.provider.onDelete = { id ->
            harness.provider.failDocQueryFor[id] = RuntimeException("provider is having a bad day")
        }

        shouldThrow<WriteException> {
            harness.gateway.delete(harness.safPath("dir", "file.txt"), recursive = false)
        }

        harness.provider.hasNode("dir", "file.txt") shouldBe true
    }

    @Test
    fun `delete surfaces an unreachable provider as WriteException`() = runTest2(autoCancel = true) {
        // "The document is gone" and "nobody answered" arrive as the same null cursor, so the
        // verification asks the provider directly. Without a provider to ask there is no proof, and a
        // delete that reported a failure must not be turned into a success.
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        harness.provider.failNextDeleteWith = IllegalArgumentException("java.io.FileNotFoundException: gone")
        harness.provider.onDelete = { harness.killProviderProcess() }

        shouldThrow<WriteException> {
            harness.gateway.delete(harness.safPath("dir", "file.txt"), recursive = false)
        }

        harness.provider.hasNode("dir", "file.txt") shouldBe true
    }

    @Test
    fun `recursive delete walks the ids the provider handed out`() = runTest2(autoCancel = true) {
        // Document ids are opaque in general and can't be rebuilt from a display name, and a provider
        // whose ids aren't path derived is exactly the kind that doesn't cascade. So here nothing below
        // the granted tree root is addressable by a rebuilt id: the fallback walk only works if it uses
        // the ids the child cursor handed it.
        val harness = harness(
            FakeDocumentsProvider.tree(FakeDocumentsProvider.IdMode.OPAQUE, opaqueFixtureIds = true) {
                file("dir/sub/file1.txt")
                file("dir/file2.txt")
            }
        )
        harness.provider.dirDeleteMode = FakeDocumentsProvider.DirDeleteMode.REJECT_NONEMPTY
        val idDir = harness.provider.resolve("dir")!!.documentId
        val idSub = harness.provider.resolve("dir", "sub")!!.documentId
        val idFile1 = harness.provider.resolve("dir", "sub", "file1.txt")!!.documentId
        val idFile2 = harness.provider.resolve("dir", "file2.txt")!!.documentId
        listOf(idDir, idSub, idFile1, idFile2).forEach { it shouldNotStartWith FakeDocumentsProvider.ROOT_ID }

        harness.gateway.delete(harness.safPath(), recursive = true)

        harness.provider.documentIds().shouldBeEmpty()
        // Children before their parents, every one of them by its opaque id.
        harness.provider.deletedDocuments shouldContainExactly listOf(
            idFile1,
            idSub,
            idFile2,
            idDir,
            FakeDocumentsProvider.ROOT_ID,
        )
    }

    @Test
    fun `delete deletes nothing during a dry run`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/file1.txt")
                file("dir/sub/file2.txt")
            }
        )

        Bugs.isDryRun = true
        try {
            harness.gateway.delete(harness.safPath("dir"), recursive = true)
        } finally {
            Bugs.isDryRun = false
        }

        harness.provider.hasNode("dir") shouldBe true
        harness.provider.hasNode("dir", "sub") shouldBe true
        harness.provider.hasNode("dir", "file1.txt") shouldBe true
        harness.provider.hasNode("dir", "sub", "file2.txt") shouldBe true
        harness.provider.deleteCalls.shouldBeEmpty()
    }

    @Test
    fun `delete without a permission surfaces as WriteException`() = runTest2(autoCancel = true) {
        // CorpseFinder only catches WriteException around its delete calls, a ReadException escaping
        // from here would abort the whole run instead of just this corpse.
        val harness = harness()

        shouldThrow<WriteException> { harness.gateway.delete(ungrantedPath("file.txt"), recursive = true) }
    }

    @Test
    fun `delete of a missing target surfaces as WriteException`() = runTest2(autoCancel = true) {
        // The initial lookup is part of the delete, not a separate read.
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })

        shouldThrow<WriteException> { harness.gateway.delete(harness.safPath("dir", "ghost.txt"), recursive = true) }
    }

    @Test
    fun `delete with a failing emptiness check surfaces as WriteException`() = runTest2(autoCancel = true) {
        // The emptiness check is the only enumeration left in delete, and an unanswerable one must not
        // read as "empty" and take the directory with it.
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        harness.provider.failChildQueryFor["root:dir"] = RuntimeException("provider is having a bad day")

        shouldThrow<WriteException> { harness.gateway.delete(harness.safPath("dir"), recursive = false) }

        harness.provider.hasNode("dir") shouldBe true
    }

    @Test
    fun `a failing fallback walk surfaces as WriteException`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/file1.txt")
                file("dir/sub/file2.txt")
            }
        )
        harness.provider.dirDeleteMode = FakeDocumentsProvider.DirDeleteMode.REJECT_NONEMPTY
        harness.provider.failChildQueryFor["root:dir/sub"] = RuntimeException("provider is having a bad day")

        shouldThrow<WriteException> { harness.gateway.delete(harness.safPath("dir"), recursive = true) }

        harness.provider.hasNode("dir") shouldBe true
    }

    @Test
    fun `delete failures surface as WriteException`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        harness.provider.failNextDeleteWith = IllegalStateException("provider is having a bad day")

        shouldThrow<WriteException> { harness.gateway.delete(harness.safPath("dir", "file.txt"), recursive = true) }
    }

    @Test
    fun `delete stays cancellable during the initial lookup`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        val scope = CoroutineScope(Dispatchers.Unconfined)
        harness.provider.onDocumentQuery = { scope.cancel() }

        cancellableDelete(scope) {
            harness.gateway.delete(harness.safPath("dir", "file.txt"), recursive = false)
        }.shouldBeInstanceOf<CancellationException>()
    }

    @Test
    fun `delete stays cancellable during the emptiness check`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })
        val scope = CoroutineScope(Dispatchers.Unconfined)
        harness.provider.onChildQuery = { scope.cancel() }

        cancellableDelete(scope) {
            harness.gateway.delete(harness.safPath("dir"), recursive = false)
        }.shouldBeInstanceOf<CancellationException>()

        // A cancelled delete deletes nothing: the checkpoint sits between the check and the call.
        harness.provider.deleteCalls.shouldBeEmpty()
        harness.provider.hasNode("dir") shouldBe true
    }

    @Test
    fun `a cancelled fallback walk stops at the current child`() = runTest2(autoCancel = true) {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/sub/file1.txt")
                file("dir/sub/file2.txt")
                file("dir/file3.txt")
            }
        )
        harness.provider.dirDeleteMode = FakeDocumentsProvider.DirDeleteMode.REJECT_NONEMPTY
        val scope = CoroutineScope(Dispatchers.Unconfined)
        harness.provider.onDelete = { if (it == "root:dir/sub/file1.txt") scope.cancel() }

        cancellableDelete(scope) {
            harness.gateway.delete(harness.safPath("dir"), recursive = true)
        }.shouldBeInstanceOf<CancellationException>()

        // The failed cascade attempt and the child that was already in flight, nothing after it.
        harness.provider.deleteCalls shouldContainExactly listOf("root:dir", "root:dir/sub/file1.txt")
        harness.provider.hasNode("dir") shouldBe true
        harness.provider.hasNode("dir", "sub") shouldBe true
        harness.provider.hasNode("dir", "sub", "file2.txt") shouldBe true
        harness.provider.hasNode("dir", "file3.txt") shouldBe true
    }

    @Test
    fun `delete stays cancellable during the document deletion`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        val scope = CoroutineScope(Dispatchers.Unconfined)
        harness.provider.onDelete = { scope.cancel() }

        cancellableDelete(scope) {
            harness.gateway.delete(harness.safPath("dir", "file.txt"), recursive = false)
        }.shouldBeInstanceOf<CancellationException>()
    }

    /** Runs [block] on [scope] and returns whatever it threw, so the cancellation shape can be asserted. */
    private suspend fun cancellableDelete(scope: CoroutineScope, block: suspend () -> Unit): Throwable? {
        var caught: Throwable? = null
        scope.launch {
            try {
                block()
            } catch (e: Throwable) {
                caught = e
            }
        }.join()
        return caught
    }

    // ------------------------------------------------------------------------------ misc writes

    @Test
    fun `createSymlink is unsupported`() = runTest2(autoCancel = true) {
        val harness = harness()

        shouldThrow<UnsupportedOperationException> {
            harness.gateway.createSymlink(harness.safPath("link"), harness.safPath("target"))
        }
    }

    @Test
    fun `setModifiedAt can not succeed over SAF`() = runTest2(autoCancel = true) {
        // documents D5: DocumentsProvider.update is final and throws, there is no DocumentsContract
        // API to set a modification time, so there is nothing to fix here.
        val harness = harness(FakeDocumentsProvider.tree { file("file.txt", lastModified = 1000L) })

        harness.gateway.setModifiedAt(harness.safPath("file.txt"), Instant.ofEpochMilli(9999L)) shouldBe false
    }

    @Test
    fun `setPermissions and setOwnership delegate to the document`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { file("file.txt", content = "12345") })
        val permissions = Permissions(0b111000000)
        val ownership = Ownership(1000L, 1000L)
        mockkStatic(Os::class)
        every { Os.fchmod(any(), permissions.mode) } just runs
        every { Os.fchown(any(), ownership.userId.toInt(), ownership.groupId.toInt()) } just runs

        harness.gateway.setPermissions(harness.safPath("file.txt"), permissions) shouldBe true
        harness.gateway.setOwnership(harness.safPath("file.txt"), ownership) shouldBe true

        verify { Os.fchmod(any(), permissions.mode) }
        verify { Os.fchown(any(), ownership.userId.toInt(), ownership.groupId.toInt()) }
        harness.provider.openedDocuments shouldContainExactly listOf(
            "root:file.txt" to "w",
            "root:file.txt" to "w",
        )
    }

    @Test
    fun `write failures are wrapped into WriteException`() = runTest2(autoCancel = true) {
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })

        // openPFD sits outside the try in SAFDocFile, so the open failure propagates to the gateway.
        shouldThrow<WriteException> {
            harness.gateway.setPermissions(harness.safPath("dir"), Permissions(0b111000000))
        }
        shouldThrow<WriteException> {
            harness.gateway.setOwnership(harness.safPath("dir"), Ownership(1000L, 1000L))
        }
        shouldThrow<WriteException> {
            harness.gateway.setModifiedAt(ungrantedPath("file.txt"), Instant.ofEpochMilli(9999L))
        }
    }
}
