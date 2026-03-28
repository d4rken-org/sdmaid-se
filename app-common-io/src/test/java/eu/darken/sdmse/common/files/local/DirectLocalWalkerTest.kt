package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.files.asFile
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.nio.file.Files

class DirectLocalWalkerTest : BaseTest() {

    private val testDir = File(IO_TEST_BASEDIR, "walker-test").absoluteFile

    @BeforeEach
    fun setup() {
        testDir.deleteRecursively()
        testDir.mkdirs()
    }

    @AfterEach
    fun cleanup() {
        testDir.deleteRecursively()
    }

    private fun List<LocalPathLookup>.names(): Set<String> = map { it.lookedUp.asFile().name }.toSet()

    @Test
    fun `walks all files and directories in tree`() = runTest {
        File(testDir, "a/b").mkdirs()
        File(testDir, "a/b/file.txt").writeText("data")
        File(testDir, "c").mkdirs()
        File(testDir, "c/file2.txt").writeText("data")
        File(testDir, "file3.txt").writeText("data")

        val walker = DirectLocalWalker(start = LocalPath.build(testDir))
        val items = walker.toList()

        items.names() shouldBe setOf("a", "b", "file.txt", "c", "file2.txt", "file3.txt")
    }

    @Test
    fun `single file emits only that file`() = runTest {
        val file = File(testDir, "single.txt").apply { writeText("data") }

        val walker = DirectLocalWalker(start = LocalPath.build(file))
        val items = walker.toList()

        items shouldHaveSize 1
        items.first().lookedUp.asFile().name shouldBe "single.txt"
    }

    @Test
    fun `empty directory emits nothing`() = runTest {
        val walker = DirectLocalWalker(start = LocalPath.build(testDir))
        val items = walker.toList()

        items shouldHaveSize 0
    }

    @Test
    fun `onFilter excludes matching items and their children`() = runTest {
        File(testDir, "keep").mkdirs()
        File(testDir, "keep/file.txt").writeText("data")
        File(testDir, "skip").mkdirs()
        File(testDir, "skip/hidden.txt").writeText("data")

        val walker = DirectLocalWalker(
            start = LocalPath.build(testDir),
            onFilter = { lookup: LocalPathLookup -> lookup.lookedUp.asFile().name != "skip" },
        )
        val items = walker.toList()

        val names = items.names()
        names shouldContain "keep"
        names shouldContain "file.txt"
        names shouldNotContain "skip"
        names shouldNotContain "hidden.txt"
    }

    @Test
    fun `followSymlinks true with cycle detection prevents infinite loop`() = runTest {
        File(testDir, "sub").mkdirs()
        File(testDir, "sub/file.txt").writeText("data")
        // Symlink must use absolute path so OS resolves it correctly
        Files.createSymbolicLink(
            File(testDir, "sub/loop").toPath(),
            testDir.toPath(),
        )

        val walker = DirectLocalWalker(
            start = LocalPath.build(testDir),
            followSymlinks = true,
        )
        val items = walker.toList()

        // Must terminate — cycle detected via canonical path.
        val names = items.names()
        names shouldContain "sub"
        names shouldContain "file.txt"
        names shouldContain "loop"
    }

    @Test
    fun `followSymlinks true descends into symlinked directory`() = runTest {
        val target = File(testDir, "target").apply { mkdirs() }
        File(target, "secret.txt").writeText("data")
        Files.createSymbolicLink(
            File(testDir, "link").toPath(),
            target.toPath(),
        )

        val walker = DirectLocalWalker(
            start = LocalPath.build(testDir),
            followSymlinks = true,
        )
        val items = walker.toList()

        val names = items.names()
        names shouldContain "target"
        names shouldContain "link"
        names shouldContain "secret.txt"
    }

    @Test
    fun `deeply nested directories are fully walked`() = runTest {
        var dir = testDir
        for (i in 1..10) {
            dir = File(dir, "level$i").apply { mkdirs() }
        }
        File(dir, "deep.txt").writeText("bottom")

        val walker = DirectLocalWalker(start = LocalPath.build(testDir))
        val items = walker.toList()

        val names = items.names()
        names shouldContain "level1"
        names shouldContain "level10"
        names shouldContain "deep.txt"
        items shouldHaveSize 11
    }
}
