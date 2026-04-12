package eu.darken.sdmse.squeezer.core.history

import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider

class VideoContentHasherTest : BaseTest() {

    private val testDir = java.io.File(IO_TEST_BASEDIR, "VideoContentHasherTest")
    private lateinit var subject: VideoContentHasher

    @BeforeEach
    fun setup() {
        if (testDir.exists()) testDir.deleteRecursively()
        testDir.mkdirs()
        subject = VideoContentHasher(
            gatewaySwitch = mockk<GatewaySwitch>(),
            dispatcherProvider = TestDispatcherProvider(),
        )
    }

    @AfterEach
    fun teardown() {
        testDir.deleteRecursively()
    }

    private fun localFile(name: String, content: ByteArray): LocalPath {
        val file = java.io.File(testDir, name)
        file.writeBytes(content)
        return LocalPath(file)
    }

    @Test
    fun `small file uses full hash - deterministic`() = runTest {
        val content = ByteArray(100_000) { (it % 256).toByte() }
        val path = localFile("small.mp4", content)

        val h1 = subject.computeHash(path)
        val h2 = subject.computeHash(path)

        h1 shouldBe h2
        // SHA-256 hex is 64 chars
        h1.contentId.value.length shouldBe 64
    }

    @Test
    fun `large file uses partial hash - deterministic`() = runTest {
        // 4 MB > 2 MB threshold so partial hash is used.
        val content = ByteArray(4 * 1024 * 1024) { (it % 256).toByte() }
        val path = localFile("large.mp4", content)

        val h1 = subject.computeHash(path)
        val h2 = subject.computeHash(path)

        h1 shouldBe h2
        h1.contentId.value.length shouldBe 64
    }

    @Test
    fun `large files with different middle - same first and last bytes - collide`() = runTest {
        // Partial hash reads first 1 MB + last 1 MB + file size. Two files that match on those
        // will collide. This test documents that as expected behavior.
        val size = 4 * 1024 * 1024
        val shared = ByteArray(size) { (it % 256).toByte() }

        val a = shared.copyOf()
        val b = shared.copyOf()
        // Mutate only the middle region (outside the first/last 1 MB window)
        for (i in (1 shl 20) + 100 until size - (1 shl 20) - 100) {
            b[i] = (b[i].toInt() xor 0xFF).toByte()
        }

        val pathA = localFile("a.mp4", a)
        val pathB = localFile("b.mp4", b)

        val hashA = subject.computeHash(pathA)
        val hashB = subject.computeHash(pathB)

        hashA shouldBe hashB
    }

    @Test
    fun `files that differ in size produce different hashes`() = runTest {
        val contentA = ByteArray(4 * 1024 * 1024) { 0x11 }
        val contentB = ByteArray((4 * 1024 * 1024) + 1) { 0x11 }

        val hashA = subject.computeHash(localFile("a.mp4", contentA))
        val hashB = subject.computeHash(localFile("b.mp4", contentB))

        hashA shouldNotBe hashB
    }

    @Test
    fun `small files with different content produce different hashes`() = runTest {
        val contentA = ByteArray(100_000) { 0x11 }
        val contentB = ByteArray(100_000) { 0x22 }

        val hashA = subject.computeHash(localFile("a.mp4", contentA))
        val hashB = subject.computeHash(localFile("b.mp4", contentB))

        hashA shouldNotBe hashB
    }

    @Test
    fun `large files with different first bytes produce different hashes`() = runTest {
        val base = ByteArray(4 * 1024 * 1024) { 0x11 }
        val modified = base.copyOf()
        modified[0] = 0x77

        val hashA = subject.computeHash(localFile("a.mp4", base))
        val hashB = subject.computeHash(localFile("b.mp4", modified))

        hashA shouldNotBe hashB
    }

    @Test
    fun `large files with different last bytes produce different hashes`() = runTest {
        val base = ByteArray(4 * 1024 * 1024) { 0x11 }
        val modified = base.copyOf()
        modified[modified.lastIndex] = 0x77

        val hashA = subject.computeHash(localFile("a.mp4", base))
        val hashB = subject.computeHash(localFile("b.mp4", modified))

        hashA shouldNotBe hashB
    }

    companion object {
        private const val IO_TEST_BASEDIR = "build/tmp/unit_tests"
    }
}
