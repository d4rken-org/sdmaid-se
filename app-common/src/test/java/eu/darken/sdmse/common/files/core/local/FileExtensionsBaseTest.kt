package eu.darken.sdmse.common.files.core.local

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.nio.file.Files

/**
 * Tests the safety property of symlink-aware recursive deletion.
 *
 * Since [isSymbolicLink] uses Android's `Os.readlink()` (unavailable on JVM),
 * these tests use `java.nio.file.Files` to create real symlinks and a NIO-based
 * mirror of the same algorithm to verify the core safety property:
 * recursive deletion must NOT follow symlinks into target directories.
 */
class FileExtensionsBaseTest : BaseTest() {

    private val testDir = File(IO_TEST_BASEDIR, "symlink-delete-test")

    @BeforeEach
    fun setup() {
        testDir.mkdirs()
    }

    @AfterEach
    fun cleanup() {
        deleteRecursivelySafeNio(testDir)
    }

    @Test
    fun `symlink to directory - target contents survive deletion`() {
        val targetDir = File(testDir, "real-data").apply { mkdirs() }
        val targetFile = File(targetDir, "precious.txt").apply { writeText("keep me") }

        val appDir = File(testDir, "app-data").apply { mkdirs() }
        val symlink = File(appDir, "sneaky-link")
        Files.createSymbolicLink(symlink.toPath(), targetDir.toPath())

        deleteRecursivelySafeNio(appDir) shouldBe true

        appDir.exists() shouldBe false
        targetDir.exists() shouldBe true
        targetFile.exists() shouldBe true
        targetFile.readText() shouldBe "keep me"
    }

    @Test
    fun `symlink to file - target file survives deletion`() {
        val targetFile = File(testDir, "real-file.txt").apply { writeText("important") }

        val appDir = File(testDir, "app-data").apply { mkdirs() }
        val symlink = File(appDir, "link-to-file")
        Files.createSymbolicLink(symlink.toPath(), targetFile.toPath())

        deleteRecursivelySafeNio(appDir) shouldBe true

        appDir.exists() shouldBe false
        targetFile.exists() shouldBe true
        targetFile.readText() shouldBe "important"
    }

    @Test
    fun `broken symlink - deleted without error`() {
        val appDir = File(testDir, "app-data").apply { mkdirs() }
        val nonExistent = File(testDir, "does-not-exist")
        val symlink = File(appDir, "broken-link")
        Files.createSymbolicLink(symlink.toPath(), nonExistent.toPath())

        Files.isSymbolicLink(symlink.toPath()) shouldBe true

        deleteRecursivelySafeNio(appDir) shouldBe true

        appDir.exists() shouldBe false
    }

    @Test
    fun `regular directory tree - deleted normally`() {
        val dir = File(testDir, "normal-dir").apply { mkdirs() }
        File(dir, "sub").apply { mkdirs() }
        File(dir, "sub/file.txt").apply { writeText("data") }
        File(dir, "root-file.txt").apply { writeText("data") }

        deleteRecursivelySafeNio(dir) shouldBe true

        dir.exists() shouldBe false
    }

    @Test
    fun `nested directory with symlink deeper in tree - target survives`() {
        val targetDir = File(testDir, "real-media").apply { mkdirs() }
        val targetFile = File(targetDir, "photo.jpg").apply { writeText("image data") }

        val appDir = File(testDir, "app-data").apply { mkdirs() }
        val subDir = File(appDir, "files").apply { mkdirs() }
        val symlink = File(subDir, "DCIM")
        Files.createSymbolicLink(symlink.toPath(), targetDir.toPath())

        File(appDir, "some-file.txt").apply { writeText("app data") }

        deleteRecursivelySafeNio(appDir) shouldBe true

        appDir.exists() shouldBe false
        targetDir.exists() shouldBe true
        targetFile.exists() shouldBe true
        targetFile.readText() shouldBe "image data"
    }

    /**
     * NIO-based mirror of [deleteRecursivelySafe] for JVM testing.
     * Same algorithm, but uses [Files.isSymbolicLink] instead of [Os.readlink].
     */
    private fun deleteRecursivelySafeNio(file: File): Boolean {
        if (Files.isSymbolicLink(file.toPath())) {
            return file.delete()
        }
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    if (!deleteRecursivelySafeNio(child)) return false
                }
            }
        }
        return file.delete()
    }
}
