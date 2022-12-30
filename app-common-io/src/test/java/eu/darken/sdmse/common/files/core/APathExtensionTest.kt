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
}
