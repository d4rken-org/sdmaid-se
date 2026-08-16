package eu.darken.sdmse.common.files.local.ipc

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.ipc.IpcClientModule
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Covers the privileged-side [FileOpsHost] against a real temp directory.
 *
 * Robolectric is mandatory: [FileOpsHost] extends the generated AIDL Binder stub.
 *
 * Deliberately NOT covered here, because the underlying `android.system.Os` calls are unavailable
 * or unshadowed on the JVM (device-only behavior):
 * - setPermissions / setOwnership (Os.chmod / Os.lchown)
 * - host-side createSymlink (Os.symlink) — symlink fixtures are created with NIO instead
 * - symlink-safe recursive deletion (depends on Os.readlink inside deleteRecursivelySafe)
 * - field-level assertions on lookUpExtended's ownership/permissions (Os.lstat)
 * - file() handles (needs a ParcelFileDescriptor harness)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class FileOpsHostTest : BaseTest() {

    private val testDir = Files.createTempDirectory("fileopshost-test").toFile()
    private val hostJob = Job()
    private val hostScope = CoroutineScope(hostJob + Dispatchers.IO)

    @After
    fun cleanup() {
        // BaseTest's @AfterAll is JUnit5-only. The streaming producers outlive collection, so the
        // scope's job has to be cancelled AND joined before the fixtures are removed.
        runBlocking { hostJob.cancelAndJoin() }
        unmockkAll()
        testDir.deleteRecursively()
    }

    private fun host() = FileOpsHost(
        appScope = hostScope,
        dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
        libcoreTool = mockk<LibcoreTool> {
            every { getNameForUid(any()) } returns null
            every { getNameForGid(any()) } returns null
        },
        ipcFunnel = mockk<IPCFunnel>(),
    )

    private fun path(file: File) = LocalPath.build(file)

    @Test
    fun `mkdirs creates the whole hierarchy`() {
        val target = File(testDir, "a/b/c")

        host().mkdirs(path(target)) shouldBe true

        target.isDirectory shouldBe true
        // java.io.File.mkdirs() reports false when there was nothing left to create.
        host().mkdirs(path(target)) shouldBe false
    }

    @Test
    fun `createNewFile creates the file and its missing parents`() {
        val target = File(testDir, "parent/child/new.txt")

        host().createNewFile(path(target)) shouldBe true

        target.isFile shouldBe true
        target.parentFile!!.isDirectory shouldBe true
        // Already there -> nothing created.
        host().createNewFile(path(target)) shouldBe false
    }

    @Test
    fun `delete removes a plain file`() {
        val target = File(testDir, "victim.txt").apply { writeText("data") }

        host().delete(path(target), recursive = false, dryRun = false) shouldBe true

        target.exists() shouldBe false
    }

    @Test
    fun `recursive delete removes a populated tree`() {
        // Plain files/dirs only: the symlink-safety of deleteRecursivelySafe() rides on Os.readlink,
        // which is device-only, so it can't be exercised here.
        val root = File(testDir, "tree").apply { mkdirs() }
        File(root, "sub/deeper").mkdirs()
        File(root, "sub/deeper/leaf.txt").writeText("leaf")
        File(root, "top.txt").writeText("top")

        host().delete(path(root), recursive = true, dryRun = false) shouldBe true

        root.exists() shouldBe false
    }

    @Test
    fun `a non-recursive delete of a populated directory fails`() {
        val root = File(testDir, "keeper").apply { mkdirs() }
        File(root, "child.txt").writeText("child")

        host().delete(path(root), recursive = false, dryRun = false) shouldBe false

        root.exists() shouldBe true
    }

    @Test
    fun `dryRun only probes write access and deletes nothing`() {
        val target = File(testDir, "dryrun.txt").apply { writeText("data") }

        host().delete(path(target), recursive = true, dryRun = true) shouldBe true

        target.exists() shouldBe true
        target.readText() shouldBe "data"
    }

    @Test
    fun `deleting an absent path is reported as success`() {
        val target = File(testDir, "never-existed.txt")

        host().delete(path(target), recursive = false, dryRun = false) shouldBe true
    }

    @Test
    fun `setModifiedAt applies the epoch millis`() {
        val target = File(testDir, "touched.txt").apply { writeText("data") }
        val stamp = 1_000_000_000_000L

        host().setModifiedAt(path(target), stamp) shouldBe true

        target.lastModified() shouldBe stamp
    }

    @Test
    fun `exists canRead and canWrite reflect the filesystem`() {
        val file = File(testDir, "probe.txt").apply { writeText("data") }
        val dir = File(testDir, "probe-dir").apply { mkdirs() }
        val missing = File(testDir, "probe-missing")
        val host = host()

        host.exists(path(file)) shouldBe true
        host.canRead(path(file)) shouldBe true
        host.canWrite(path(file)) shouldBe true

        host.exists(path(dir)) shouldBe true
        host.canRead(path(dir)) shouldBe true
        host.canWrite(path(dir)) shouldBe true

        host.exists(path(missing)) shouldBe false
        host.canRead(path(missing)) shouldBe false
        host.canWrite(path(missing)) shouldBe false
    }

    @Test
    fun `du sums every node of the tree`() {
        val root = File(testDir, "du").apply { mkdirs() }
        val sub = File(root, "sub").apply { mkdirs() }
        val fileA = File(root, "a.txt").apply { writeText("aaaa") }
        val fileB = File(sub, "b.txt").apply { writeText("bbbbbbbb") }

        // The payload byte count alone is not the expectation: directory entries have a
        // platform-dependent length() that du() includes.
        val expected = listOf(root, sub, fileA, fileB).sumOf { it.length() }

        host().du(path(root)) shouldBe expected
    }

    @Test
    fun `lookUp classifies files, directories and symlinks`() {
        val file = File(testDir, "lookup.txt").apply { writeText("12345") }
        val dir = File(testDir, "lookup-dir").apply { mkdirs() }
        val link = File(testDir, "lookup-link")
        Files.createSymbolicLink(link.toPath(), file.toPath())
        val host = host()

        host.lookUp(path(file)).apply {
            fileType shouldBe FileType.FILE
            size shouldBe 5L
            lookedUp shouldBe path(file)
        }

        host.lookUp(path(dir)).fileType shouldBe FileType.DIRECTORY

        host.lookUp(path(link)).apply {
            fileType shouldBe FileType.SYMBOLIC_LINK
            target shouldBe path(file)
        }
    }

    @Test
    fun `lookUp of a missing path fails`() {
        val thrown = shouldThrow<UnsupportedOperationException> {
            host().lookUp(path(File(testDir, "gone")))
        }
        thrown.message!! shouldStartWith "eu.darken.sdmse.common.files.ReadException: "
    }

    @Test
    fun `listFilesStream streams the directory contents`() {
        val dir = File(testDir, "listing").apply { mkdirs() }
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")
        File(dir, "sub").mkdirs()

        runBlocking {
            val collected = host().listFilesStream(path(dir)).toLocalPathFlow().toList()
            collected.map { it.name } shouldContainExactlyInAnyOrder listOf("a.txt", "b.txt", "sub")
        }
    }

    @Test
    fun `lookupFilesStream streams lookups for the directory contents`() {
        val dir = File(testDir, "lookup-listing").apply { mkdirs() }
        File(dir, "a.txt").writeText("aa")
        File(dir, "sub").mkdirs()

        runBlocking {
            val collected = host().lookupFilesStream(path(dir)).toLocalPathLookupFlow().toList()
            collected.map { it.name } shouldContainExactlyInAnyOrder listOf("a.txt", "sub")
            collected.single { it.name == "a.txt" }.apply {
                fileType shouldBe FileType.FILE
                size shouldBe 2L
            }
            collected.single { it.name == "sub" }.fileType shouldBe FileType.DIRECTORY
        }
    }

    @Test
    fun `lookupFilesExtendedStream produces frames for the directory contents`() {
        val dir = File(testDir, "extended-listing").apply { mkdirs() }
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")

        runBlocking {
            val collected = host().lookupFilesExtendedStream(path(dir)).toLocalPathLookupExtendedFlow().toList()
            // Only the lookup part is asserted, ownership/permissions come from Os.lstat.
            collected.map { it.name } shouldContainExactlyInAnyOrder listOf("a.txt", "b.txt")
        }
    }

    @Test
    fun `walkStream descends into subdirectories`() {
        val root = File(testDir, "walk").apply { mkdirs() }
        File(root, "sub/deeper").mkdirs()
        File(root, "sub/deeper/leaf.txt").writeText("leaf")
        File(root, "top.txt").writeText("top")

        runBlocking {
            val collected = host()
                .walkStream(path(root), emptyList(), false)
                .toLocalPathLookupFlow()
                .toList()
            collected.map { it.name } shouldContainExactlyInAnyOrder listOf("sub", "deeper", "leaf.txt", "top.txt")
        }
    }

    @Test
    fun `walkStream honors the pathDoesNotContain filter`() {
        val root = File(testDir, "walk-filtered").apply { mkdirs() }
        File(root, "skipme/deeper").mkdirs()
        File(root, "skipme/deeper/leaf.txt").writeText("leaf")
        File(root, "keep.txt").writeText("keep")

        runBlocking {
            val collected = host()
                .walkStream(path(root), listOf("skipme"), false)
                .toLocalPathLookupFlow()
                .toList()
            // Filtered entries are not descended into either.
            collected.map { it.name } shouldContainExactlyInAnyOrder listOf("keep.txt")
        }
    }

    @Test
    fun `walkStream does not follow symlinked directories when disabled`() {
        val root = File(testDir, "walk-symlink").apply { mkdirs() }
        val outside = File(testDir, "outside").apply { mkdirs() }
        File(outside, "hidden.txt").writeText("hidden")
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())

        runBlocking {
            val collected = host()
                .walkStream(path(root), emptyList(), false)
                .toLocalPathLookupFlow()
                .toList()
            collected.map { it.name } shouldContainExactlyInAnyOrder listOf("link")
        }
    }

    @Test
    fun `an enumeration failure surfaces at collection time, not as a binder throw`() {
        val missing = File(testDir, "not-a-directory")

        runBlocking {
            // The stream is handed out fine, the host-side listing only fails once it runs, so the
            // failure travels as an error frame and is reconstructed by the decoder.
            val stream = host().lookupFilesStream(path(missing))
            val thrown = runCatching { stream.toLocalPathLookupFlow().toList() }.exceptionOrNull()
            thrown.shouldBeInstanceOf<IOException>()
            thrown.message shouldBe "File does not exist"
        }
    }

    @Test
    fun `host errors are wrapped for the binder and unwrap back into the original type`() {
        val dir = File(testDir, "existing-dir").apply { mkdirs() }

        // Parcel.writeException() only supports a handful of types, so the host encodes the original
        // class + message into an UnsupportedOperationException (IpcHostModule.wrapToPropagate).
        val thrown = shouldThrow<UnsupportedOperationException> {
            host().createNewFile(path(dir))
        }
        thrown.message!! shouldStartWith "java.io.IOException: "

        // ...which the client side turns back into the original exception type.
        val refined = with(object : IpcClientModule {}) { thrown.refineException() }
        refined.shouldBeInstanceOf<IOException>()
        refined.message shouldBe "Can't create file, path exists and is directory: ${path(dir)}"
    }
}
