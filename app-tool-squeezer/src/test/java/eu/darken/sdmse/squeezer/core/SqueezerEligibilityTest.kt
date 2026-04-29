package eu.darken.sdmse.squeezer.core

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class SqueezerEligibilityTest : BaseTest() {

    private val testDir = File(IO_TEST_BASEDIR, "SqueezerEligibilityTest")

    @BeforeEach
    fun setup() {
        if (testDir.exists()) testDir.deleteRecursively()
        testDir.mkdirs()
    }

    @AfterEach
    fun teardown() {
        // Re-enable bits so deleteRecursively can actually clean up.
        testDir.walkTopDown().forEach {
            it.setReadable(true)
            it.setWritable(true)
        }
        testDir.deleteRecursively()
    }

    @Test
    fun `OK for a readable file under a writable parent`() {
        val file = File(testDir, "ok.bin").apply { writeBytes(ByteArray(10)) }

        SqueezerEligibility.check(file) shouldBe SqueezerEligibility.Verdict.OK
    }

    @Test
    fun `NOT_A_FILE for a directory`() {
        val dir = File(testDir, "subdir").apply { mkdirs() }

        SqueezerEligibility.check(dir) shouldBe SqueezerEligibility.Verdict.NOT_A_FILE
    }

    @Test
    fun `NOT_A_FILE for a missing path`() {
        val missing = File(testDir, "missing.bin")

        SqueezerEligibility.check(missing) shouldBe SqueezerEligibility.Verdict.NOT_A_FILE
    }

    @Test
    fun `UNREADABLE when read bit is stripped`() {
        val file = File(testDir, "unreadable.bin").apply { writeBytes(ByteArray(10)) }

        // Need to be running as a non-root user for chmod to take effect. Gradle test JVM
        // normally is, but guard so CI running as root skips cleanly instead of flapping.
        assumeTrue(file.setReadable(false, false) && !file.canRead())

        SqueezerEligibility.check(file) shouldBe SqueezerEligibility.Verdict.UNREADABLE
    }

    @Test
    fun `PARENT_NOT_WRITABLE when parent dir is read-only`() {
        val parent = File(testDir, "locked-parent").apply { mkdirs() }
        val file = File(parent, "file.bin").apply { writeBytes(ByteArray(10)) }

        assumeTrue(parent.setWritable(false, false) && !parent.canWrite())

        SqueezerEligibility.check(file) shouldBe SqueezerEligibility.Verdict.PARENT_NOT_WRITABLE
    }

    @Test
    fun `NOT_LOCAL for a SAFPath`() {
        val safPath = mockk<SAFPath>(relaxed = true)

        SqueezerEligibility.check(safPath) shouldBe SqueezerEligibility.Verdict.NOT_LOCAL
    }

    @Test
    fun `NOT_LOCAL for a null path`() {
        SqueezerEligibility.check(null) shouldBe SqueezerEligibility.Verdict.NOT_LOCAL
    }

    @Test
    fun `OK via LocalPath overload`() {
        val file = File(testDir, "ok.bin").apply { writeBytes(ByteArray(10)) }
        val localPath = LocalPath.build(file)

        SqueezerEligibility.check(localPath) shouldBe SqueezerEligibility.Verdict.OK
    }

    companion object {
        private const val IO_TEST_BASEDIR = "build/tmp/unit_tests"
    }
}
