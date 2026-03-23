package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ContentItemTest : BaseTest() {

    @Test
    fun `inaccessible directory with no size returns null`() {
        val item = ContentItem.fromInaccessible(LocalPath.build("test"))
        item.size.shouldBeNull()
        item.inaccessible shouldBe true
    }

    @Test
    fun `inaccessible directory with explicit size returns that size`() {
        val item = ContentItem.fromInaccessible(LocalPath.build("test"), 1000L)
        item.size shouldBe 1000L
    }

    @Test
    fun `file size is just itemSize`() {
        val item = ContentItem(
            path = LocalPath.build("test", "file.txt"),
            lookup = null,
            itemSize = 500L,
            type = FileType.FILE,
            inaccessible = false,
        )
        item.size shouldBe 500L
    }

    @Test
    fun `directory size includes children recursively`() {
        val child1 = ContentItem(
            path = LocalPath.build("dir", "file1.txt"),
            lookup = null,
            itemSize = 100L,
            type = FileType.FILE,
            inaccessible = false,
        )
        val child2 = ContentItem(
            path = LocalPath.build("dir", "file2.txt"),
            lookup = null,
            itemSize = 200L,
            type = FileType.FILE,
            inaccessible = false,
        )
        val dir = ContentItem(
            path = LocalPath.build("dir"),
            lookup = null,
            itemSize = 4096L,
            type = FileType.DIRECTORY,
            children = setOf(child1, child2),
            inaccessible = false,
        )
        dir.size shouldBe 4096L + 100L + 200L
    }

    @Test
    fun `directory with null itemSize returns null even with children`() {
        val child = ContentItem(
            path = LocalPath.build("dir", "file.txt"),
            lookup = null,
            itemSize = 100L,
            type = FileType.FILE,
            inaccessible = false,
        )
        val dir = ContentItem(
            path = LocalPath.build("dir"),
            lookup = null,
            itemSize = null,
            type = FileType.DIRECTORY,
            children = setOf(child),
            inaccessible = true,
        )
        dir.size.shouldBeNull()
    }

    @Test
    fun `nested directories sum sizes recursively`() {
        val file = ContentItem(
            path = LocalPath.build("a", "b", "file.txt"),
            lookup = null,
            itemSize = 1000L,
            type = FileType.FILE,
            inaccessible = false,
        )
        val innerDir = ContentItem(
            path = LocalPath.build("a", "b"),
            lookup = null,
            itemSize = 4096L,
            type = FileType.DIRECTORY,
            children = setOf(file),
            inaccessible = false,
        )
        val outerDir = ContentItem(
            path = LocalPath.build("a"),
            lookup = null,
            itemSize = 4096L,
            type = FileType.DIRECTORY,
            children = setOf(innerDir),
            inaccessible = false,
        )
        outerDir.size shouldBe 4096L + 4096L + 1000L
    }

    @Test
    fun `child with null size contributes 0 to parent`() {
        val inaccessibleChild = ContentItem.fromInaccessible(LocalPath.build("dir", "subdir"))
        val dir = ContentItem(
            path = LocalPath.build("dir"),
            lookup = null,
            itemSize = 4096L,
            type = FileType.DIRECTORY,
            children = setOf(inaccessibleChild),
            inaccessible = false,
        )
        dir.size shouldBe 4096L
    }
}
