package eu.darken.sdmse.common.files.core.local

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Tests symlink helpers and the safety property of symlink-aware recursive deletion.
 *
 * [deleteRecursivelySafe] uses Android's `Os.readlink()` (unavailable on JVM), so its recursion
 * is verified via [deleteRecursivelySafeNio] — a NIO-based mirror of the same algorithm — to
 * assert the core safety property: recursive deletion must NOT follow symlinks into target
 * directories. [isSymbolicLink] is NIO-based ([Files.isSymbolicLink]) and is tested directly.
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

    @Test
    fun `listFilesStreaming - parity with listFiles2 over a populated directory`() {
        val dir = File(testDir, "pop").apply { mkdirs() }
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")
        File(dir, "sub").mkdirs()
        File(dir, ".hidden").writeText("h")

        val streamed = runBlocking { dir.listFilesStreaming().toList() }.map { it.name }.toSet()
        val eager = dir.listFiles2().map { it.name }.toSet()
        // Order is unspecified for both, so compare as sets.
        streamed shouldBe eager
        streamed shouldBe setOf("a.txt", "b.txt", "sub", ".hidden")
    }

    @Test
    fun `listFilesStreaming - empty directory yields nothing`() {
        val dir = File(testDir, "empty").apply { mkdirs() }
        runBlocking { dir.listFilesStreaming().toList() } shouldBe emptyList()
    }

    @Test
    fun `listFilesStreaming - missing path throws IOException`() {
        val dir = File(testDir, "does-not-exist")
        shouldThrow<IOException> { runBlocking { dir.listFilesStreaming().toList() } }
    }

    @Test
    fun `listFilesStreaming - a regular file throws IOException`() {
        val file = File(testDir, "regular.txt").apply { writeText("x") }
        // newDirectoryStream throws NotDirectoryException, normalized to a plain IOException.
        shouldThrow<IOException> { runBlocking { file.listFilesStreaming().toList() } }
    }

    @Test
    fun `listFilesStreaming - symlink child is listed but not followed`() {
        val dir = File(testDir, "withlink").apply { mkdirs() }
        val target = File(testDir, "target").apply { mkdirs() }
        File(target, "inside.txt").writeText("x")
        Files.createSymbolicLink(File(dir, "link").toPath(), target.toPath())
        File(dir, "plain.txt").writeText("r")

        val names = runBlocking { dir.listFilesStreaming().toList() }.map { it.name }.toSet()
        // The symlink itself is an entry; its target's contents are not enumerated.
        names shouldBe setOf("link", "plain.txt")
    }

    @Test
    fun `isSymbolicLink classifies link nodes including broken ones`() {
        val regularFile = File(testDir, "regular.txt").apply { writeText("data") }
        val regularDir = File(testDir, "regular-dir").apply { mkdirs() }
        val nonExistent = File(testDir, "ghost")

        val linkToFile = File(testDir, "link-to-file")
        Files.createSymbolicLink(linkToFile.toPath(), regularFile.toPath())
        val linkToDir = File(testDir, "link-to-dir")
        Files.createSymbolicLink(linkToDir.toPath(), regularDir.toPath())
        val brokenLink = File(testDir, "broken-link")
        Files.createSymbolicLink(brokenLink.toPath(), nonExistent.toPath())

        // A symlink node is a symlink regardless of whether its target exists/is readable.
        linkToFile.isSymbolicLink() shouldBe true
        linkToDir.isSymbolicLink() shouldBe true
        brokenLink.isSymbolicLink() shouldBe true

        regularFile.isSymbolicLink() shouldBe false
        regularDir.isSymbolicLink() shouldBe false
        nonExistent.isSymbolicLink() shouldBe false
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
