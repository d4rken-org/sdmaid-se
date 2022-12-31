package eu.darken.sdmse.common.files.core

import android.net.Uri
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.LocalPathLookup
import eu.darken.sdmse.common.files.core.saf.SAFPath
import eu.darken.sdmse.common.files.core.saf.SAFPathLookup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class APathExtensionTest : BaseTest() {
    private val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")

    @Test
    fun `match operator - LocalPath`() {
        val file1: APath = LocalPath.build("test", "file1")
        val file2: APath = LocalPath.build("test", "file2")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
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


    @Test
    fun `match operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "test", "file1")
        val file2: APath = SAFPath.build(treeUri, "test", "file2")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file1"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file2"),
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

    @Test
    fun `match operator - mixes types`() {
        val file1: APath = LocalPath.build("test", "file1")
        val file2: APath = SAFPath.build(treeUri, "test", "file2")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "test", "file2"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        shouldThrow<IllegalArgumentException> {
            file1.matches(file2)
        }
        shouldThrow<IllegalArgumentException> {
            file2.matches(file1)
        }
        shouldThrow<IllegalArgumentException> {
            lookup1.matches(lookup2)
        }
        shouldThrow<IllegalArgumentException> {
            lookup2.matches(lookup1)
        }
    }

    @Test
    fun `isAncestorOf operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = LocalPath.build("parent", "child", "niece")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child", "niece"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `isAncestorOf operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child", "niece")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child", "niece"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `isAncestorOf operator - mixed types`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child", "niece")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child", "niece"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        shouldThrow<IllegalArgumentException> {
            file1.matches(file2)
        }
        shouldThrow<IllegalArgumentException> {
            file2.matches(file1)
        }
        shouldThrow<IllegalArgumentException> {
            lookup1.matches(lookup2)
        }
        shouldThrow<IllegalArgumentException> {
            lookup2.matches(lookup1)
        }
    }

    @Test
    fun `isDescendantOf operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = LocalPath.build("parent", "child", "niece")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child", "niece"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `isDescendantOf operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child", "niece")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child", "niece"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `isDescendantOf operator - mixed types`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child", "niece")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child", "niece"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        shouldThrow<IllegalArgumentException> {
            file1.matches(file2)
        }
        shouldThrow<IllegalArgumentException> {
            file2.matches(file1)
        }
        shouldThrow<IllegalArgumentException> {
            lookup1.matches(lookup2)
        }
        shouldThrow<IllegalArgumentException> {
            lookup2.matches(lookup1)
        }
    }

    @Test
    fun `isParentOf operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = LocalPath.build("parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `isParentOf operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `isParentOf operator - mixed types`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        shouldThrow<IllegalArgumentException> {
            file1.matches(file2)
        }
        shouldThrow<IllegalArgumentException> {
            file2.matches(file1)
        }
        shouldThrow<IllegalArgumentException> {
            lookup1.matches(lookup2)
        }
        shouldThrow<IllegalArgumentException> {
            lookup2.matches(lookup1)
        }
    }

    @Test
    fun `isChildOf operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = LocalPath.build("parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `isChildOf operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `isChildOf operator - mixed types`() {
        val file1: APath = LocalPath.build("parent")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        shouldThrow<IllegalArgumentException> {
            file1.isChildOf(file2)
        }
        shouldThrow<IllegalArgumentException> {
            file2.isChildOf(file1)
        }
        shouldThrow<IllegalArgumentException> {
            lookup1.isChildOf(lookup2)
        }
        shouldThrow<IllegalArgumentException> {
            lookup2.isChildOf(lookup1)
        }
    }


    @Test
    fun `startsWith operator - LocalPath`() {
        val file1: APath = LocalPath.build("parent", "chi")
        val file2: APath = LocalPath.build("parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "chi"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `startsWith operator - SAFPath`() {
        val file1: APath = SAFPath.build(treeUri, "parent", "chi")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "chi"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
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

    @Test
    fun `startsWith operator - mixed types`() {
        val file1: APath = LocalPath.build("parent", "chi")
        val file2: APath = SAFPath.build(treeUri, "parent", "child")

        val lookup1: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("parent", "chi"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "parent", "child"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        shouldThrow<IllegalArgumentException> {
            file1.startsWith(file2)
        }
        shouldThrow<IllegalArgumentException> {
            file2.startsWith(file1)
        }
        shouldThrow<IllegalArgumentException> {
            lookup1.startsWith(lookup2)
        }
        shouldThrow<IllegalArgumentException> {
            lookup2.startsWith(lookup1)
        }
    }

    @Test fun `remove prefix - LocalPath`() {
        val prefix: APath = LocalPath.build("pre", "fix")
        val pre: APath = LocalPath.build("pre")

        val prefixLookup: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("pre", "fix"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val preLookup: APathLookup<*> = LocalPathLookup(
            lookedUp = LocalPath.build("pre"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )

        prefix.removePrefix(prefix) shouldBe emptyList()

        shouldThrow<IllegalArgumentException> {
            pre.removePrefix(prefix)
        }
        prefix.removePrefix(pre) shouldBe listOf("fix")
        prefix.removePrefix(preLookup) shouldBe listOf("fix")

        prefixLookup.removePrefix(prefixLookup) shouldBe emptyList()

        shouldThrow<IllegalArgumentException> {
            preLookup.removePrefix(prefixLookup)
        }
        prefixLookup.removePrefix(preLookup) shouldBe listOf("fix")
        prefixLookup.removePrefix(pre) shouldBe listOf("fix")
    }

    @Test fun `remove prefix - SAFPath`() {
        val prefix: APath = SAFPath.build(treeUri, "pre", "fix")
        val pre: APath = SAFPath.build(treeUri, "pre")
        val prefixLookup: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "pre", "fix"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val preLookup: APathLookup<*> = SAFPathLookup(
            lookedUp = SAFPath.build(treeUri, "pre"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )

        prefix.removePrefix(prefix) shouldBe emptyList()

        shouldThrow<IllegalArgumentException> {
            pre.removePrefix(prefix)
        }
        prefix.removePrefix(pre) shouldBe listOf("fix")
        prefix.removePrefix(preLookup) shouldBe listOf("fix")

        prefixLookup.removePrefix(prefixLookup) shouldBe emptyList()

        shouldThrow<IllegalArgumentException> {
            preLookup.removePrefix(prefixLookup)
        }
        prefixLookup.removePrefix(preLookup) shouldBe listOf("fix")
        prefixLookup.removePrefix(pre) shouldBe listOf("fix")
    }

    @Test fun `remove prefix - mixed types`() {
        val prefix: APath = LocalPath.build("pre", "fix")
        val pre: APath = SAFPath.build(treeUri, "pre")

        prefix.removePrefix(prefix) shouldBe emptyList()

        shouldThrow<IllegalArgumentException> {
            pre.removePrefix(prefix)
        }
        shouldThrow<IllegalArgumentException> {
            prefix.removePrefix(pre) shouldBe listOf("pre")
        }
        shouldThrow<IllegalArgumentException> {
            prefix.removePrefix(pre) shouldBe listOf("pre", "fix")
        }
    }

    @Test
    fun `test segment matches`() {
        emptyList<String>().matches(emptyList()) shouldBe true
        null.matches(listOf("abc", "def")) shouldBe false
        listOf("abc", "def").matches(null) shouldBe false
        listOf("abc", "def").matches(listOf("abc", "def")) shouldBe true
        listOf("abc", "DEF").matches(listOf("abc", "def")) shouldBe false
        listOf("abc", "DEF").matches(listOf("abc", "def"), ignoreCase = true) shouldBe true
    }

    @Test
    fun `test segment isAncestorOf`() {
        emptyList<String>().isAncestorOf(emptyList()) shouldBe false
        null.isAncestorOf(listOf("abc", "def")) shouldBe false
        listOf("abc", "def").isAncestorOf(null) shouldBe false
        listOf("abc").isAncestorOf(listOf("abc", "def")) shouldBe true
        listOf("ABC").isAncestorOf(listOf("abc", "def")) shouldBe false
        listOf("ABC").isAncestorOf(listOf("abc", "def"), ignoreCase = true) shouldBe true
    }

    @Test
    fun `test segment contains`() {
        emptyList<String>().contains(emptyList()) shouldBe true
        listOf("abc", "def", "ghi").contains(listOf("abc", "def", "ghi")) shouldBe true
        listOf("abc", "def", "ghi").contains(listOf("abc", "def")) shouldBe true
        listOf("abc", "def", "ghi").contains(listOf("def")) shouldBe true
        listOf("abc", "DEF", "ghi").contains(listOf("def")) shouldBe false
        listOf("abc", "DEF", "ghi").contains(listOf("def"), ignoreCase = true) shouldBe true
    }

    @Test
    fun `test segment startsWith`() {
        emptyList<String>().startsWith(emptyList()) shouldBe true
        null.startsWith(listOf("abc", "def")) shouldBe false
        listOf("abc", "def").startsWith(null) shouldBe false

        listOf("abc", "def").startsWith(listOf("abc", "def")) shouldBe true
        listOf("abc", "def").startsWith(listOf("abc", "de")) shouldBe true
        listOf("abc", "def").startsWith(listOf("abc")) shouldBe true
        listOf("abc", "def").startsWith(listOf("ab")) shouldBe true

        listOf("ABc", "def").startsWith(listOf("abc", "def")) shouldBe false
        listOf("ABc", "def").startsWith(listOf("abc", "def"), ignoreCase = true) shouldBe true

        listOf("ABc", "def").startsWith(listOf("abc", "de")) shouldBe false
        listOf("ABc", "def").startsWith(listOf("abc", "de"), ignoreCase = true) shouldBe true

        listOf("ABc", "def").startsWith(listOf("abc")) shouldBe false
        listOf("ABc", "def").startsWith(listOf("abc"), ignoreCase = true) shouldBe true

        listOf("ABc", "def").startsWith(listOf("ab")) shouldBe false
        listOf("ABc", "def").startsWith(listOf("ab"), ignoreCase = true) shouldBe true
    }
}
