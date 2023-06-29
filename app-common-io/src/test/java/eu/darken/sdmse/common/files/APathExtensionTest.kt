package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.saf.SAFDocFile
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.files.saf.SAFPathLookup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class APathExtensionTest : BaseTest() {
    private val treeUri = "content://com.android.externalstorage.documents/tree/primary%3A"

    @Test fun `match operator - LocalPath`() {
        val file1: APath = LocalPath.build("test", "file1")
        val file2: APath = LocalPath.build("test", "file2")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
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

    @Test fun `match operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "test", "file1")
        val file2: APath = SAFPath.build(treeUri, "test", "file2")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file1"),
            docFile = mockk<SAFDocFile>(),
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file2"),
            docFile = mockk<SAFDocFile>(),
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

    @Test fun `match operator - mixes types`() {
        val file1: APath = LocalPath.build("test", "file1")
        val file2: APath = SAFPath.build(treeUri, "test", "file2")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file2"),
            docFile = mockk<SAFDocFile>(),
        )

        file1.matches(file2) shouldBe false
        file2.matches(file1) shouldBe false

        lookup1.matches(lookup2) shouldBe false
        lookup2.matches(lookup1) shouldBe false
    }

    @Test fun `isAncestorOf operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = LocalPath.build("parent", "child", "niece")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
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

    @Test fun `isAncestorOf operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child", "niece")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent"),
            docFile = mockk<SAFDocFile>(),
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child", "niece"),
            docFile = mockk<SAFDocFile>(),
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

    @Test fun `isAncestorOf operator - mixed types`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child", "niece")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child", "niece"),
            docFile = mockk<SAFDocFile>(),
        )

        file1.matches(file2) shouldBe false
        file2.matches(file1) shouldBe false

        lookup1.matches(lookup2) shouldBe false
        lookup2.matches(lookup1) shouldBe false
    }

    @Test fun `isDescendantOf operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = LocalPath.build("parent", "child", "niece")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
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

    @Test fun `isDescendantOf operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child", "niece")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent"),
            docFile = mockk<SAFDocFile>(),
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child", "niece"),
            docFile = mockk<SAFDocFile>(),
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

    @Test fun `isDescendantOf operator - mixed types`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child", "niece")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child", "niece"),
            docFile = mockk<SAFDocFile>(),
        )

        file1.matches(file2) shouldBe false

        file2.matches(file1) shouldBe false

        lookup1.matches(lookup2) shouldBe false

        lookup2.matches(lookup1) shouldBe false
    }

    @Test fun `isParentOf operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = LocalPath.build("parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
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

    @Test fun `isParentOf operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent"),
            docFile = mockk<SAFDocFile>(),
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            docFile = mockk<SAFDocFile>(),
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

    @Test fun `isParentOf operator - mixed types`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            docFile = mockk<SAFDocFile>(),
        )

        file1.matches(file2)
        file2.matches(file1)

        lookup1.matches(lookup2)
        lookup2.matches(lookup1)
    }

    @Test fun `isChildOf operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = LocalPath.build("parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
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

    @Test fun `isChildOf operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent"),
            docFile = mockk<SAFDocFile>(),
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            docFile = mockk<SAFDocFile>(),
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

    @Test fun `isChildOf operator - mixed types`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            docFile = mockk<SAFDocFile>(),
        )

        file1.isChildOf(file2)
        file2.isChildOf(file1)

        lookup1.isChildOf(lookup2)
        lookup2.isChildOf(lookup1)
    }


    @Test fun `startsWith operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent", "chi")
        val file2: APath = LocalPath.build("parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "chi"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child"),
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
    }

    @Test fun `startsWith operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent", "chi")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "chi"),
            docFile = mockk<SAFDocFile>(),
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            docFile = mockk<SAFDocFile>(),
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
    }

    @Test fun `startsWith operator - mixed types`() {
        val file1: APath = LocalPath.build("parent", "chi")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "chi"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            docFile = mockk<SAFDocFile>(),
        )

        file1.startsWith(file2)
        file2.startsWith(file1)

        lookup1.startsWith(lookup2)
        lookup2.startsWith(lookup1)
    }

    @Test fun `remove prefix - LocalPath`() {
        val prefix: APath = LocalPath.build("pre", "fix")
        val pre: APath = LocalPath.build("pre")

        val prefixLookup: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("pre", "fix"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val preLookup: APathLookup<*> = LocalPathLookup(
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

    @Test fun `remove prefix - SAFPath`() {
        val prefix: APath = SAFPath.build(treeUri, "pre", "fix")
        val pre: APath = SAFPath.build(treeUri, "pre")
        val prefixLookup: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "pre", "fix"),
            docFile = mockk<SAFDocFile>(),
        )
        val preLookup: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "pre"),
            docFile = mockk<SAFDocFile>(),
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

    @Test fun `remove prefix - mixed types`() {
        val prefix: APath = LocalPath.build("pre", "fix")
        val pre: APath = SAFPath.build(treeUri, "pre")

        prefix.removePrefix(prefix) shouldBe segs()

        shouldThrow<IllegalArgumentException> {
            pre.removePrefix(prefix)
        }
        shouldThrow<IllegalArgumentException> {
            prefix.removePrefix(pre) shouldBe segs("pre")
        }
        shouldThrow<IllegalArgumentException> {
            prefix.removePrefix(pre) shouldBe segs("pre", "fix")
        }
    }

    @Test fun `remove prefix with overlap - LocalPath`() {
        val prefix: APath = LocalPath.build("prefix", "overlap", "folder")
        val pre: APath = LocalPath.build("prefix", "overlap")

        val prefixLookup: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("prefix", "overlap", "folder"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val preLookup: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("prefix", "overlap"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )

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

    @Test fun `remove prefix with overlap - SAFPath`() {
        val prefix: APath = SAFPath.build(treeUri, "prefix", "overlap", "folder")
        val pre: APath = SAFPath.build(treeUri, "prefix", "overlap")
        val prefixLookup: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "prefix", "overlap", "folder"),
            docFile = mockk(),
        )
        val preLookup: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "prefix", "overlap"),
            docFile = mockk(),
        )

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

    @Test fun `remove prefix with overlap - mixed types`() {
        val prefix: APath = LocalPath.build("prefix", "overlap", "folder")
        val pre: APath = SAFPath.build(treeUri, "prefix", "overlap")

        shouldThrow<IllegalArgumentException> {
            pre.removePrefix(prefix, overlap = 1)
        }
        shouldThrow<IllegalArgumentException> {
            prefix.removePrefix(pre, overlap = 1)
        }
        shouldThrow<IllegalArgumentException> {
            prefix.removePrefix(pre, overlap = 1)
        }
    }

    @Test fun `filterDistinctRoots operator - LocalPath`() {
        val file1: APath = LocalPath.build("test", "file1")
        val file1s: APath = LocalPath.build("test", "file1", "sub")
        val file2: APath = LocalPath.build("test", "file2")
        val file2s: APath = LocalPath.build("test", "file2", "sub")

        setOf(file1, file1s, file2, file2s).filterDistinctRoots() shouldBe setOf(file1, file2)
    }

    @Test fun `filterDistinctRoots operator - LocalPath - edgecase caught`() {
        val file1: APath = LocalPath.build("data", "log", "knoxsdk.log.0.lck")
        val file2: APath = LocalPath.build("data", "log", "knoxsdk.log.0")
        val file3: APath = LocalPath.build("data", "log", "knoxsdk.log.0.1.lck")
        val file4: APath = LocalPath.build("data", "log", "knoxsdk.log.0.1")

        setOf(file1, file2, file3, file4).filterDistinctRoots() shouldBe setOf(file1, file2, file3, file4)
    }

    @Test fun `filterDistinctRoots operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "test", "file1")
        val file1s: APath = SAFPath.build(treeUri, "test", "file1", "sub")
        val file2: APath = SAFPath.build(treeUri, "test", "file2")
        val file2s: APath = SAFPath.build(treeUri, "test", "file2", "sub")

        setOf(file1, file1s, file2, file2s).filterDistinctRoots() shouldBe setOf(file1, file2)
    }

    @Test fun `filterDistinctRoots operator - mixes types`() {
        val file1: APath = LocalPath.build("test", "file1")
        val file1s: APath = LocalPath.build("test", "file1", "sub")
        val file2: APath = SAFPath.build(treeUri, "test", "file2")
        val file2s: APath = SAFPath.build(treeUri, "test", "file2", "sub")

        setOf(file1, file1s, file2, file2s).filterDistinctRoots() shouldBe setOf(file1, file2)
    }
}
