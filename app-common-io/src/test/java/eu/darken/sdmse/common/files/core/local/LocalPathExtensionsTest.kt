package eu.darken.sdmse.common.files.core.local

import eu.darken.sdmse.common.files.core.FileType
import eu.darken.sdmse.common.files.core.matches
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class LocalPathExtensionsTest : BaseTest() {


    @Test
    fun `test chunking`() {
        val parent = LocalPath.build("/the/parent")
        val child = LocalPath.build("/the/parent/has/a/child")

        val crumbs = parent.crumbsTo(child)

        crumbs shouldBe arrayOf("has", "a", "child")
    }

    @Test
    fun `parent child relationship`() {
        val parent = LocalPath.build("base", "the", "parent")
        parent.isAncestorOf(LocalPath.build("base", "the")) shouldBe false
        parent.isAncestorOf(LocalPath.build("base", "the", "parent")) shouldBe false
        parent.isAncestorOf(LocalPath.build("base", "the", "parent2")) shouldBe false
        parent.isAncestorOf(LocalPath.build("base", "the", "parent", "child")) shouldBe true
        parent.isAncestorOf(LocalPath.build("base", "the", "parent", "child", "child")) shouldBe true
        parent.isAncestorOf(LocalPath.build("base", "the", "parent", "child1", "child2")) shouldBe true
    }

    @Test
    fun `match operator`() {
        val file1 = LocalPath.build("test", "file1")
        val file2 = LocalPath.build("test", "file2")

        val lookup1 = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2 = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file2"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        file1.matches(file1) shouldBe true
        file1.matches(file2) shouldBe false
        file1.matches(lookup1) shouldBe true
        file1.matches(lookup2) shouldBe false
        lookup1.matches(file1) shouldBe true
        lookup1.matches(file2) shouldBe false
        lookup1.matches(lookup1) shouldBe true
        lookup1.matches(lookup2) shouldBe false
        file2.matches(lookup2) shouldBe true
    }
}