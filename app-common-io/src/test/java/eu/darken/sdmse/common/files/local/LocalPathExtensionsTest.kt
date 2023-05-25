package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.local.*
import eu.darken.sdmse.common.files.startsWith
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class LocalPathExtensionsTest : BaseTest() {

    @Test fun `test chunking`() {
        val parent = LocalPath.build("/the/parent")
        val child = LocalPath.build("/the/parent/has/a/child")

        val crumbs = parent.crumbsTo(child)

        crumbs shouldBe arrayOf("has", "a", "child")
    }

    @Test fun `path isAncestorOf check`() {
        LocalPath.build("parent").isAncestorOf(LocalPath.build()) shouldBe false
        LocalPath.build().isAncestorOf(LocalPath.build("parent")) shouldBe true
        LocalPath.build().isAncestorOf(LocalPath.build()) shouldBe false

        LocalPath.build("parent").isAncestorOf(LocalPath.build("parent")) shouldBe false
        LocalPath.build("parent").isAncestorOf(LocalPath.build("parent2")) shouldBe false

        LocalPath.build("parent").isAncestorOf(LocalPath.build("parent", "child")) shouldBe true
        LocalPath.build("parent").isAncestorOf(LocalPath.build("parent", "child", "child")) shouldBe true
        LocalPath.build("parent").isAncestorOf(LocalPath.build("parent", "child1", "child2")) shouldBe true

        LocalPath.build("parent1", "child").isAncestorOf(LocalPath.build("parent", "child")) shouldBe false
    }

    @Test fun `path isDescendant check`() {
        LocalPath.build().isDescendantOf(LocalPath.build()) shouldBe false
        LocalPath.build().isDescendantOf(LocalPath.build("parent")) shouldBe false
        LocalPath.build("child").isDescendantOf(LocalPath.build()) shouldBe true

        LocalPath.build("parent", "child").isDescendantOf(LocalPath.build("parent")) shouldBe true
        LocalPath.build("child1").isDescendantOf(LocalPath.build("child2")) shouldBe false
    }

    @Test fun `path isParent check`() {
        LocalPath.build().isParentOf(LocalPath.build()) shouldBe false
        LocalPath.build().isParentOf(LocalPath.build("parent")) shouldBe true
        LocalPath.build("parent").isParentOf(LocalPath.build("parent", "child")) shouldBe true
        LocalPath.build("parent").isParentOf(LocalPath.build("parent", "child1", "child2")) shouldBe false
        LocalPath.build("parent", "child1").isParentOf(LocalPath.build("parent", "child1", "child2")) shouldBe true
        LocalPath.build("parent", "child1").isParentOf(LocalPath.build("parent", "child1")) shouldBe false
        LocalPath.build("").isParentOf(LocalPath.build("child")) shouldBe true
    }

    @Test fun `path isChild check`() {
        LocalPath.build().isChildOf(LocalPath.build()) shouldBe false
        LocalPath.build("child").isChildOf(LocalPath.build()) shouldBe true
        LocalPath.build("child").isChildOf(LocalPath.build("parent")) shouldBe false
        LocalPath.build("child").isChildOf(LocalPath.build("child")) shouldBe false

        LocalPath.build("parent").isChildOf(LocalPath.build("parent", "child")) shouldBe false
        LocalPath.build("parent", "child").isChildOf(LocalPath.build("parent", "child")) shouldBe false
        LocalPath.build("parent", "child").isChildOf(LocalPath.build("parent")) shouldBe true
        LocalPath.build("parent", "child1", "child2").isChildOf(LocalPath.build("parent")) shouldBe false
    }

    @Test fun `match operator`() {
        val file1 = LocalPath.build("test", "file1")
        val file2 = LocalPath.build("test", "file2")

        val lookup1 = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2 = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file2"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
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


    @Test fun `isAncestorOf operator`() {
        val file1 = LocalPath.build("parent")
        val file2 = LocalPath.build("parent", "child", "niece")

        val lookup1 = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2 = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child", "niece"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )

        file1.isAncestorOf(file1) shouldBe false
        file1.isAncestorOf(file2) shouldBe true
        file1.isAncestorOf(lookup1) shouldBe false
        file1.isAncestorOf(lookup2) shouldBe true

        file2.isAncestorOf(file1) shouldBe false
        file2.isAncestorOf(file2) shouldBe false
        file2.isAncestorOf(lookup1) shouldBe false
        file2.isAncestorOf(lookup2) shouldBe false

        lookup1.isAncestorOf(file1) shouldBe false
        lookup1.isAncestorOf(file2) shouldBe true
        lookup1.isAncestorOf(lookup1) shouldBe false
        lookup1.isAncestorOf(lookup2) shouldBe true

        lookup2.isAncestorOf(file1) shouldBe false
        lookup2.isAncestorOf(file2) shouldBe false
        lookup2.isAncestorOf(lookup1) shouldBe false
        lookup2.isAncestorOf(lookup2) shouldBe false
    }

    @Test fun `isDescendantOf operator`() {
        val file1 = LocalPath.build("parent")
        val file2 = LocalPath.build("parent", "child", "niece")

        val lookup1 = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2 = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child", "niece"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )

        file1.isDescendantOf(file1) shouldBe false
        file1.isDescendantOf(file2) shouldBe false
        file1.isDescendantOf(lookup1) shouldBe false
        file1.isDescendantOf(lookup2) shouldBe false

        file2.isDescendantOf(file1) shouldBe true
        file2.isDescendantOf(file2) shouldBe false
        file2.isDescendantOf(lookup1) shouldBe true
        file2.isDescendantOf(lookup2) shouldBe false

        lookup1.isDescendantOf(file1) shouldBe false
        lookup1.isDescendantOf(file2) shouldBe false
        lookup1.isDescendantOf(lookup1) shouldBe false
        lookup1.isDescendantOf(lookup2) shouldBe false

        lookup2.isDescendantOf(file1) shouldBe true
        lookup2.isDescendantOf(file2) shouldBe false
        lookup2.isDescendantOf(lookup1) shouldBe true
        lookup2.isDescendantOf(lookup2) shouldBe false
    }


    @Test fun `isParentOf operator`() {
        val file1 = LocalPath.build("parent")
        val file2 = LocalPath.build("parent", "child")

        val lookup1 = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2 = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )

        file1.isParentOf(file1) shouldBe false
        file1.isParentOf(file2) shouldBe true
        file1.isParentOf(lookup1) shouldBe false
        file1.isParentOf(lookup2) shouldBe true

        file2.isParentOf(file1) shouldBe false
        file2.isParentOf(file2) shouldBe false
        file2.isParentOf(lookup1) shouldBe false
        file2.isParentOf(lookup2) shouldBe false

        lookup1.isParentOf(file1) shouldBe false
        lookup1.isParentOf(file2) shouldBe true
        lookup1.isParentOf(lookup1) shouldBe false
        lookup1.isParentOf(lookup2) shouldBe true

        lookup2.isParentOf(file1) shouldBe false
        lookup2.isParentOf(file2) shouldBe false
        lookup2.isParentOf(lookup1) shouldBe false
        lookup2.isParentOf(lookup2) shouldBe false
    }

    @Test fun `isChildOf operator`() {
        val file1 = LocalPath.build("parent")
        val file2 = LocalPath.build("parent", "child")

        val lookup1 = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2 = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )

        file1.isChildOf(file1) shouldBe false
        file1.isChildOf(file2) shouldBe false
        file1.isChildOf(lookup1) shouldBe false
        file1.isChildOf(lookup2) shouldBe false

        file2.isChildOf(file1) shouldBe true
        file2.isChildOf(file2) shouldBe false
        file2.isChildOf(lookup1) shouldBe true
        file2.isChildOf(lookup2) shouldBe false

        lookup1.isChildOf(file1) shouldBe false
        lookup1.isChildOf(file2) shouldBe false
        lookup1.isChildOf(lookup1) shouldBe false
        lookup1.isChildOf(lookup2) shouldBe false

        lookup2.isChildOf(file1) shouldBe true
        lookup2.isChildOf(file2) shouldBe false
        lookup2.isChildOf(lookup1) shouldBe true
        lookup2.isChildOf(lookup2) shouldBe false
    }

    @Test fun `startsWith operator`() {
        val file1 = LocalPath.build("chi")
        val file2 = LocalPath.build("child")

        val lookup1 = LocalPathLookup(
            lookedUp = LocalPath.build("chi"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2 = LocalPathLookup(
            lookedUp = LocalPath.build("child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )

        file1.startsWith(file1) shouldBe true
        file1.startsWith(file2) shouldBe false
        file1.startsWith(lookup1) shouldBe true
        file1.startsWith(lookup2) shouldBe false

        file2.startsWith(file1) shouldBe true
        file2.startsWith(file2) shouldBe true
        file2.startsWith(lookup1) shouldBe true
        file2.startsWith(lookup2) shouldBe true

        lookup1.startsWith(file1) shouldBe true
        lookup1.startsWith(file2) shouldBe false
        lookup1.startsWith(lookup1) shouldBe true
        lookup1.startsWith(lookup2) shouldBe false

        lookup2.startsWith(file1) shouldBe true
        lookup2.startsWith(file2) shouldBe true
        lookup2.startsWith(lookup1) shouldBe true
        lookup2.startsWith(lookup2) shouldBe true


        val file3 = LocalPath.build("/data/app/eu.thedarken.sdm.test-")
        val file4 = LocalPath.build("/data/app/eu.thedarken.sdm.test-2/base.apk")
        file4.startsWith(file3) shouldBe true
    }

    @Test fun `remove prefix`() {
        val prefix = LocalPath.build("pre", "fix")
        val pre = LocalPath.build("pre")

        val prefixLookup = LocalPathLookup(
            lookedUp = LocalPath.build("pre", "fix"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val preLookup = LocalPathLookup(
            lookedUp = LocalPath.build("pre"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )

        prefix.removePrefix(prefix) shouldBe segs()

        shouldThrow<IllegalArgumentException> {
            pre.removePrefix(prefix)
        }
        prefix.removePrefix(pre) shouldBe segs("fix")
        prefix.removePrefix(preLookup) shouldBe segs("fix")

        prefixLookup.removePrefix(prefixLookup) shouldBe segs()

        shouldThrow<IllegalArgumentException> {
            preLookup.removePrefix(prefixLookup)
        }
        prefixLookup.removePrefix(preLookup) shouldBe segs("fix")
        prefixLookup.removePrefix(pre) shouldBe segs("fix")
    }


    @Test fun `remove prefix with overlap`() {
        val prefix = LocalPath.build("prefix", "overlap", "folder")
        val pre = LocalPath.build("prefix", "overlap")

        val prefixLookup = LocalPathLookup(
            lookedUp = LocalPath.build("prefix", "overlap", "folder"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val preLookup = LocalPathLookup(
            lookedUp = LocalPath.build("prefix", "overlap"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )

        prefix.removePrefix(prefix, overlap = 0) shouldBe prefix.removePrefix(prefix)

        prefix.removePrefix(prefix, overlap = 1) shouldBe segs("folder")

        shouldThrow<IllegalArgumentException> {
            pre.removePrefix(prefix, overlap = 1)
        }
        prefix.removePrefix(pre, overlap = 1) shouldBe segs("overlap", "folder")
        prefix.removePrefix(preLookup, overlap = 1) shouldBe segs("overlap", "folder")

        prefixLookup.removePrefix(prefixLookup, overlap = 1) shouldBe segs("folder")

        shouldThrow<IllegalArgumentException> {
            preLookup.removePrefix(prefixLookup, overlap = 1)
        }
        prefixLookup.removePrefix(preLookup, overlap = 1) shouldBe segs("overlap", "folder")
        prefixLookup.removePrefix(pre, overlap = 1) shouldBe segs("overlap", "folder")
    }
}