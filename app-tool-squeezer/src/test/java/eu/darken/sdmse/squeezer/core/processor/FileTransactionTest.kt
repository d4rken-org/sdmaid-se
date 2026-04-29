package eu.darken.sdmse.squeezer.core.processor

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File

class FileTransactionTest : BaseTest() {

    /**
     * Real-filesystem-backed FileOps with predicate-based injection points for simulating
     * specific failures. Tests that need a vanilla FileOps use this with no blockers
     * configured. Tests for restore/swap failures set the blocker lambdas to reject specific
     * (from, to) rename/copy pairs — this granularity is needed because the swap and restore
     * steps both have `target` as the rename destination but differ in the source file.
     */
    private class FakeFileOps : FileOps {
        var renameBlocker: (from: File, to: File) -> Boolean = { _, _ -> false }
        var copyBlocker: (from: File, to: File) -> Boolean = { _, _ -> false }
        var setLastModifiedBlocker: (file: File, time: Long) -> Boolean = { _, _ -> false }
        val setLastModifiedCalls = mutableListOf<Pair<File, Long>>()

        override suspend fun exists(file: File): Boolean = file.exists()
        override suspend fun canRead(file: File): Boolean = file.canRead()
        override suspend fun length(file: File): Long = file.length()

        override suspend fun delete(file: File): Boolean {
            if (!file.exists()) return true
            return file.delete()
        }

        override suspend fun renameTo(from: File, to: File): Boolean {
            if (renameBlocker(from, to)) return false
            return from.renameTo(to)
        }

        override suspend fun mkdirs(dir: File): Boolean = if (dir.exists()) true else dir.mkdirs()

        override suspend fun createFile(file: File): Boolean = try {
            file.createNewFile()
        } catch (e: Exception) {
            false
        }

        override suspend fun listFiles(dir: File): List<File> =
            dir.listFiles()?.toList() ?: emptyList()

        override suspend fun copyFile(from: File, to: File) {
            if (copyBlocker(from, to)) throw java.io.IOException("simulated copy failure")
            from.inputStream().use { input ->
                to.outputStream().use { output -> input.copyTo(output) }
            }
        }

        override suspend fun getLastModified(file: File): Long = file.lastModified()

        override suspend fun setLastModified(file: File, time: Long): Boolean {
            setLastModifiedCalls.add(file to time)
            if (setLastModifiedBlocker(file, time)) return false
            return file.setLastModified(time)
        }
    }

    private val testDir = File(IO_TEST_BASEDIR, "FileTransactionTest")
    private lateinit var fileOps: FakeFileOps
    private lateinit var subject: FileTransaction

    @BeforeEach
    fun setup() {
        if (testDir.exists()) testDir.deleteRecursively()
        testDir.mkdirs()
        fileOps = FakeFileOps()
        subject = FileTransaction(
            dispatcherProvider = TestDispatcherProvider(),
            fileOps = fileOps,
        )
    }

    @AfterEach
    fun teardown() {
        testDir.deleteRecursively()
    }

    private fun targetWith(content: ByteArray): File {
        val file = File(testDir, "target.bin")
        file.writeBytes(content)
        return file
    }

    private fun workDir(target: File) = File(target.parentFile, FileTransaction.WORKDIR_NAME)

    private fun backupFile(target: File) =
        File(workDir(target), "${FileTransaction.BACKUP_PREFIX}${target.name}")

    private fun tempFile(target: File) =
        File(workDir(target), "${FileTransaction.TEMP_PREFIX}${target.name}")

    @Test
    fun `happy path - smaller replacement swaps and cleans up`() = runTest {
        val original = ByteArray(1000) { it.toByte() }
        val replacement = ByteArray(400) { it.toByte() }
        val target = targetWith(original)

        val outcome = subject.replace(target, dryRun = false) { tempFile ->
            tempFile.writeBytes(replacement)
        }

        outcome.replaced shouldBe true
        outcome.originalSize shouldBe 1000L
        outcome.replacementSize shouldBe 400L
        outcome.savedBytes shouldBe 600L

        target.exists() shouldBe true
        target.length() shouldBe 400L
        target.readBytes() shouldBe replacement

        backupFile(target).exists() shouldBe false
        tempFile(target).exists() shouldBe false
        workDir(target).exists() shouldBe false
    }

    @Test
    fun `no savings - original untouched when replacement is larger`() = runTest {
        val original = ByteArray(500) { 0x11 }
        val target = targetWith(original)

        val outcome = subject.replace(target, dryRun = false) { tempFile ->
            tempFile.writeBytes(ByteArray(600) { 0x22 })
        }

        outcome.replaced shouldBe false
        outcome.savedBytes shouldBe 0L
        target.length() shouldBe 500L
        target.readBytes() shouldBe original
        backupFile(target).exists() shouldBe false
        tempFile(target).exists() shouldBe false
    }

    @Test
    fun `no savings - original untouched when replacement is equal size`() = runTest {
        val original = ByteArray(500) { 0x11 }
        val target = targetWith(original)

        val outcome = subject.replace(target, dryRun = false) { tempFile ->
            tempFile.writeBytes(ByteArray(500) { 0x22 })
        }

        outcome.replaced shouldBe false
        target.readBytes() shouldBe original
        tempFile(target).exists() shouldBe false
    }

    @Test
    fun `dry run - reports savings without swapping`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)

        val outcome = subject.replace(target, dryRun = true) { tempFile ->
            tempFile.writeBytes(ByteArray(250) { 0x22 })
        }

        outcome.replaced shouldBe false
        outcome.savedBytes shouldBe 750L
        target.length() shouldBe 1000L
        target.readBytes() shouldBe original
        tempFile(target).exists() shouldBe false
    }

    @Test
    fun `produce throws - original untouched and temp cleaned up`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)

        val caught = runCatching {
            subject.replace(target, dryRun = false) { tempFile ->
                tempFile.writeBytes(ByteArray(100))
                throw RuntimeException("boom")
            }
        }

        caught.isFailure shouldBe true
        caught.exceptionOrNull()?.message shouldBe "boom"
        target.length() shouldBe 1000L
        target.readBytes() shouldBe original
        backupFile(target).exists() shouldBe false
        tempFile(target).exists() shouldBe false
    }

    @Test
    fun `produce leaves empty temp - treated as failure`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)

        val caught = runCatching {
            subject.replace(target, dryRun = false) { _ ->
                // Produces nothing
            }
        }

        caught.isFailure shouldBe true
        caught.exceptionOrNull().shouldBeInstanceOf<java.io.IOException>()
        target.length() shouldBe 1000L
    }

    @Test
    fun `orphan recovery - backup with missing target is restored`() = runTest {
        val original = ByteArray(800) { it.toByte() }
        // Simulate a prior run that died after rename(target → backup).
        val target = File(testDir, "target.bin")
        val work = workDir(target)
        work.mkdirs()
        backupFile(target).writeBytes(original)
        // target is missing

        val outcome = subject.replace(target, dryRun = false) { tempFile ->
            tempFile.writeBytes(ByteArray(200))
        }

        outcome.replaced shouldBe true
        target.exists() shouldBe true
        target.length() shouldBe 200L
        // The earlier orphan backup is gone — either consumed during recovery or overwritten
        backupFile(target).exists() shouldBe false
    }

    @Test
    fun `orphan recovery - backup alongside intact target is removed as stale`() = runTest {
        val original = ByteArray(800) { 0x33 }
        val staleBackup = ByteArray(800) { 0x44 }
        val target = targetWith(original)
        workDir(target).mkdirs()
        backupFile(target).writeBytes(staleBackup)

        val outcome = subject.replace(target, dryRun = false) { tempFile ->
            tempFile.writeBytes(ByteArray(100))
        }

        outcome.replaced shouldBe true
        target.length() shouldBe 100L
        backupFile(target).exists() shouldBe false
    }

    @Test
    fun `orphan recovery - stale temp file is cleaned before produce`() = runTest {
        val original = ByteArray(800) { 0x33 }
        val target = targetWith(original)
        workDir(target).mkdirs()
        tempFile(target).writeBytes(ByteArray(50) { 0x55 })

        val outcome = subject.replace(target, dryRun = false) { tempFile ->
            tempFile.length() shouldBe 0L
            tempFile.writeBytes(ByteArray(100) { 0x77 })
        }

        outcome.replaced shouldBe true
        target.length() shouldBe 100L
    }

    @Test
    fun `nomedia marker is created when workdir is first used`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)

        subject.replace(target, dryRun = false) { tempFile ->
            // At the moment produce runs, the workdir must exist with a .nomedia marker.
            File(workDir(target), ".nomedia").exists() shouldBe true
            tempFile.writeBytes(ByteArray(500))
        }
    }

    @Test
    fun `workdir removed after successful run leaves nothing behind`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)

        subject.replace(target, dryRun = false) { tempFile ->
            tempFile.writeBytes(ByteArray(400))
        }

        workDir(target).exists() shouldBe false
    }

    @Test
    fun `cancellation during produce - original untouched`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)

        val caught = runCatching {
            subject.replace(target, dryRun = false) { tempFile ->
                tempFile.writeBytes(ByteArray(200))
                throw kotlinx.coroutines.CancellationException("cancelled")
            }
        }

        caught.isFailure shouldBe true
        target.length() shouldBe 1000L
        target.readBytes() shouldBe original
    }

    // --- Failure-injection tests exercising paths that the real filesystem won't trigger ---

    @Test
    fun `swap fails - original restored from backup and backup removed`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)

        val expectedTemp = tempFile(target)

        // Fail the swap rename (tempFile → target). No copy fallback — we fail closed.
        // The restore rename (backupFile → target) is NOT blocked, so the original is restored.
        fileOps.renameBlocker = { from, to -> from == expectedTemp && to == target }

        val caught = runCatching {
            subject.replace(target, dryRun = false) { tempFile ->
                tempFile.writeBytes(ByteArray(400) { 0x22 })
            }
        }

        caught.isFailure shouldBe true
        // Original data preserved by the restore path.
        target.exists() shouldBe true
        target.length() shouldBe 1000L
        target.readBytes() shouldBe original
        backupFile(target).exists() shouldBe false
    }

    @Test
    fun `swap AND restore fail - backup preserved on disk for manual recovery`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)

        val expectedTemp = tempFile(target)
        val expectedBackup = backupFile(target)

        // Block the swap rename (temp → target) AND the restore rename
        // (backup → target). This is the worst-case branch the safety contract is built for.
        fileOps.renameBlocker = { from, to ->
            (from == expectedTemp && to == target) ||
                (from == expectedBackup && to == target)
        }

        val caught = runCatching {
            subject.replace(target, dryRun = false) { tempFile ->
                tempFile.writeBytes(ByteArray(400) { 0x22 })
            }
        }

        caught.isFailure shouldBe true
        val message = caught.exceptionOrNull()?.message ?: ""
        message.contains("Failed to restore backup") shouldBe true

        // CRITICAL invariant: the backup file must still be on disk for manual recovery.
        expectedBackup.exists() shouldBe true
        expectedBackup.readBytes() shouldBe original
    }

    @Test
    fun `initial backup rename fails - original untouched`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)

        val expectedBackup = backupFile(target)

        // Block only the very first swap step: rename(original → backup).
        fileOps.renameBlocker = { from, to -> from == target && to == expectedBackup }

        val caught = runCatching {
            subject.replace(target, dryRun = false) { tempFile ->
                tempFile.writeBytes(ByteArray(400) { 0x22 })
            }
        }

        caught.isFailure shouldBe true
        target.exists() shouldBe true
        target.length() shouldBe 1000L
        target.readBytes() shouldBe original
        expectedBackup.exists() shouldBe false
        tempFile(target).exists() shouldBe false
    }

    // --- Timestamp preservation tests (issue #2388) ---

    @Test
    fun `target retains original mtime after successful swap`() = runTest {
        val original = ByteArray(1000) { 0x11 }
        val target = targetWith(original)
        val originalMtime = FIXED_MTIME_MILLIS
        target.setLastModified(originalMtime) shouldBe true

        val outcome = subject.replace(target, dryRun = false) { tempFile ->
            tempFile.writeBytes(ByteArray(300) { 0x22 })
        }

        outcome.replaced shouldBe true
        target.lastModified() shouldBe originalMtime
    }

    @Test
    fun `no-savings path does not touch mtime`() = runTest {
        // Replacement same size → transaction aborts before any rename.
        val target = targetWith(ByteArray(500) { 0x11 })

        subject.replace(target, dryRun = false) { tempFile ->
            tempFile.writeBytes(ByteArray(500) { 0x22 })
        }

        fileOps.setLastModifiedCalls.shouldBeEmpty()
    }

    @Test
    fun `dryRun does not touch mtime`() = runTest {
        val target = targetWith(ByteArray(1000) { 0x11 })

        subject.replace(target, dryRun = true) { tempFile ->
            tempFile.writeBytes(ByteArray(300) { 0x22 })
        }

        fileOps.setLastModifiedCalls.shouldBeEmpty()
    }

    @Test
    fun `setLastModified failure does not fail the transaction`() = runTest {
        val target = targetWith(ByteArray(1000) { 0x11 })
        fileOps.setLastModifiedBlocker = { _, _ -> true }

        val outcome = subject.replace(target, dryRun = false) { tempFile ->
            tempFile.writeBytes(ByteArray(300) { 0x22 })
        }

        outcome.replaced shouldBe true
        target.length() shouldBe 300L
        backupFile(target).exists() shouldBe false
        tempFile(target).exists() shouldBe false
    }

    companion object {
        private const val IO_TEST_BASEDIR = "build/tmp/unit_tests"

        private const val FIXED_MTIME_MILLIS = 1_600_000_000_000L
    }
}
