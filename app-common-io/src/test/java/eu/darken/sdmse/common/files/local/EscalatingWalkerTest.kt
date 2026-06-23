package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.FileType
import io.kotest.matchers.collections.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.nio.file.Files
import java.time.Instant

class EscalatingWalkerTest : BaseTest() {

    private val testDir = File(IO_TEST_BASEDIR, "escalating-walker-test").absoluteFile
    private val gateway = mockk<LocalGateway>()

    @BeforeEach
    fun setup() {
        testDir.deleteRecursively()
        testDir.mkdirs()
    }

    private val externalTarget = File(IO_TEST_BASEDIR, "escalating-walker-target.txt").absoluteFile

    @AfterEach
    fun cleanup() {
        unmockkAll()
        testDir.deleteRecursively()
        externalTarget.delete()
    }

    private fun lookup(path: LocalPath, fileType: FileType) = LocalPathLookup(
        lookedUp = path,
        fileType = fileType,
        size = 4096L,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    @Test
    fun `followSymlinks escalates a symlink whose target the app cannot resolve`() = runTest {
        val start = LocalPath.build(testDir)
        // Broken/privileged symlink: its target can't be stat'd by the app process, mirroring a
        // /system symlink whose target requires root.
        val symlinkFile = File(testDir, "privlink")
        Files.createSymbolicLink(symlinkFile.toPath(), File("/nonexistent/privileged/target").toPath())
        val symlinkPath = LocalPath.build(symlinkFile)
        val escalatedChild = lookup(LocalPath.build(File(symlinkFile, "found.txt")), FileType.FILE)

        // Os.readlink (symlink classification) is unavailable on the JVM, so stub the child lookup
        // as a SYMBOLIC_LINK; exists() still runs against the real (unresolvable) symlink on disk.
        mockkStatic("eu.darken.sdmse.common.files.local.LocalPathExtensionsKt")
        every { any<LocalPath>().performLookup() } returns lookup(symlinkPath, FileType.SYMBOLIC_LINK)

        coEvery { gateway.lookup(start) } returns lookup(start, FileType.DIRECTORY)
        coEvery { gateway.hasRoot() } returns true
        coEvery { gateway.hasAdb() } returns false
        coEvery {
            gateway.walk(symlinkPath, any(), LocalGateway.Mode.ROOT)
        } returns flowOf(escalatedChild)

        val items = EscalatingWalker(
            gateway = gateway,
            start = start,
            options = APathGateway.WalkOptions(followSymlinks = true),
        ).toList()

        val names = items.map { it.lookedUp.name }.toSet()
        names shouldContain "privlink"       // the symlink node itself
        names shouldContain "found.txt"      // resolved host-side via the escalated walk
        coVerify(exactly = 1) { gateway.walk(symlinkPath, any(), LocalGateway.Mode.ROOT) }
    }

    @Test
    fun `followSymlinks does not escalate a symlink whose target the app can resolve`() = runTest {
        val start = LocalPath.build(testDir)
        val realFile = externalTarget.apply { writeText("data") }
        val symlinkFile = File(testDir, "filelink")
        Files.createSymbolicLink(symlinkFile.toPath(), realFile.toPath())
        val symlinkPath = LocalPath.build(symlinkFile)

        mockkStatic("eu.darken.sdmse.common.files.local.LocalPathExtensionsKt")
        every { any<LocalPath>().performLookup() } returns lookup(symlinkPath, FileType.SYMBOLIC_LINK)

        coEvery { gateway.lookup(start) } returns lookup(start, FileType.DIRECTORY)
        coEvery { gateway.hasRoot() } returns true
        coEvery { gateway.hasAdb() } returns false

        val items = EscalatingWalker(
            gateway = gateway,
            start = start,
            options = APathGateway.WalkOptions(followSymlinks = true),
        ).toList()

        val names = items.map { it.lookedUp.name }.toSet()
        names shouldContain "filelink"
        // Target (real.txt) is app-readable, so the symlink resolves app-side → no escalation needed.
        coVerify(exactly = 0) { gateway.walk(any(), any(), LocalGateway.Mode.ROOT) }
    }
}
