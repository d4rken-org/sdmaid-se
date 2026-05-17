package eu.darken.sdmse.common.storage

import android.content.Context
import android.util.Log
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File

class StorageRescueTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private lateinit var baseDir: File
    private lateinit var rescueFile: File
    private lateinit var context: Context
    private val dispatcherProvider = TestDispatcherProvider()

    private val rescueSize: Long = 2L * 1024 * 1024
    private val createThreshold: Long = 10L * 1024 * 1024
    private val releaseThreshold: Long = 2L * 1024 * 1024

    @BeforeEach
    fun setup() {
        // releaseIfNeeded uses android.util.Log directly because it runs before
        // the project's Logging system is installed. Stub Log in JVM tests.
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        baseDir = spyk(File(tempDir, "no_backup").apply { mkdirs() })
        rescueFile = File(baseDir, "storage_rescue.bin")
        context = mockk(relaxed = true)
        every { context.noBackupFilesDir } returns baseDir
    }

    @AfterEach
    fun teardown() {
        rescueFile.delete()
        baseDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    private fun newRescue() = StorageRescue(context, dispatcherProvider)

    // -------- restoreIfPossible --------

    @Test
    fun `restoreIfPossible allocates rescue when space is comfortable`() = runTest {
        every { baseDir.usableSpace } returns 100L * 1024 * 1024

        newRescue().restoreIfPossible()

        rescueFile.exists() shouldBe true
        rescueFile.length() shouldBe rescueSize
    }

    @Test
    fun `restoreIfPossible no-ops when rescue already correct size`() = runTest {
        every { baseDir.usableSpace } returns 100L * 1024 * 1024
        rescueFile.writeBytes(ByteArray(rescueSize.toInt()))
        val originalLastModified = rescueFile.lastModified()
        Thread.sleep(20)

        newRescue().restoreIfPossible()

        rescueFile.exists() shouldBe true
        rescueFile.length() shouldBe rescueSize
        rescueFile.lastModified() shouldBe originalLastModified
    }

    @Test
    fun `restoreIfPossible cleans wrong-size leftover and recreates`() = runTest {
        every { baseDir.usableSpace } returns 100L * 1024 * 1024
        rescueFile.writeBytes(ByteArray(123))

        newRescue().restoreIfPossible()

        rescueFile.exists() shouldBe true
        rescueFile.length() shouldBe rescueSize
    }

    @Test
    fun `restoreIfPossible skips when usable space well below CREATE_THRESHOLD`() = runTest {
        every { baseDir.usableSpace } returns 0L

        newRescue().restoreIfPossible()

        rescueFile.exists() shouldBe false
    }

    @Test
    fun `restoreIfPossible skips when usable space exactly at CREATE_THRESHOLD - 1`() = runTest {
        every { baseDir.usableSpace } returns createThreshold - 1

        newRescue().restoreIfPossible()

        rescueFile.exists() shouldBe false
    }

    @Test
    fun `restoreIfPossible allocates when usable space exactly at CREATE_THRESHOLD`() = runTest {
        every { baseDir.usableSpace } returns createThreshold

        newRescue().restoreIfPossible()

        rescueFile.exists() shouldBe true
        rescueFile.length() shouldBe rescueSize
    }

    @Test
    fun `restoreIfPossible is idempotent under repeated invocations`() = runTest {
        every { baseDir.usableSpace } returns 100L * 1024 * 1024
        val rescue = newRescue()

        val jobs = (1..5).map { async { rescue.restoreIfPossible() } }
        jobs.awaitAll()

        rescueFile.exists() shouldBe true
        rescueFile.length() shouldBe rescueSize
    }

    // -------- releaseIfNeeded --------

    @Test
    fun `releaseIfNeeded deletes rescue when usable space below threshold`() {
        every { baseDir.usableSpace } returns releaseThreshold - 1
        rescueFile.writeBytes(ByteArray(rescueSize.toInt()))

        StorageRescue.releaseIfNeeded(context)

        rescueFile.exists() shouldBe false
    }

    @Test
    fun `releaseIfNeeded keeps rescue when usable space at or above threshold`() {
        every { baseDir.usableSpace } returns releaseThreshold
        rescueFile.writeBytes(ByteArray(rescueSize.toInt()))

        StorageRescue.releaseIfNeeded(context)

        rescueFile.exists() shouldBe true
    }

    @Test
    fun `releaseIfNeeded is a no-op when no rescue file exists`() {
        every { baseDir.usableSpace } returns 0L
        rescueFile.exists() shouldBe false

        StorageRescue.releaseIfNeeded(context)

        rescueFile.exists() shouldBe false
    }

    @Test
    fun `releaseIfNeeded swallows exceptions and does not throw`() {
        every { context.noBackupFilesDir } throws SecurityException("denied")

        StorageRescue.releaseIfNeeded(context) // must not throw
    }
}
