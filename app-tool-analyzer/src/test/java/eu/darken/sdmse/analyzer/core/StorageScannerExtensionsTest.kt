package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.storage.findContent
import eu.darken.sdmse.analyzer.core.storage.toFlatContent
import eu.darken.sdmse.analyzer.core.storage.toNestedContent
import eu.darken.sdmse.analyzer.core.storage.walkContentItem
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StorageScannerExtensionsTest : BaseTest() {

    @Test
    fun `content item children nesting variant 1`() {
        val item0 = ContentItem.fromInaccessible(LocalPath.build())
        val item1 = ContentItem.fromInaccessible(LocalPath.build("folder1"))
        val item2A = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2A"))
        val item2B = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2B"))
        val item3A = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2A", "folder3"))
        val item3B = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2B", "folder3"))

        val preNesting = setOf(item0, item1, item2A, item2B, item3A, item3B)
        val postNesting = preNesting.toNestedContent()

        val item0Post = postNesting.single()
        item0Post.path shouldBe item0.path

        val item1Post = item0Post.children.single()
        item1Post.path shouldBe item1.path

        item1Post.children.apply {
            size shouldBe 2

            single { it.path == item2B.path }
        }

        item1Post.children.single { it.path == item2A.path }.children.apply {
            size shouldBe 1
            single { it.path == item3A.path }
        }

        item1Post.children.single { it.path == item2B.path }.children.apply {
            size shouldBe 1
            single { it.path == item3B.path }
        }
    }

    @Test
    fun `content item children nesting variant 2`() {
        val item0 = ContentItem.fromInaccessible(LocalPath.build(""))
        val item1 = ContentItem.fromInaccessible(LocalPath.build("folder1"))
        val item2A = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2A"))
        val item2B = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2B"))
        val item3A = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2A", "folder3"))
        val item3B = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2B", "folder3"))

        val preNesting = setOf(item0, item1, item2A, item2B, item3A, item3B)
        val postNesting = preNesting.toNestedContent()

        val item0Post = postNesting.single()
        item0Post.path shouldBe item0.path

        val item1Post = item0Post.children.single()
        item1Post.path shouldBe item1.path

        item1Post.children.apply {
            size shouldBe 2

            single { it.path == item2B.path }
        }

        item1Post.children.single { it.path == item2A.path }.children.apply {
            size shouldBe 1
            single { it.path == item3A.path }
        }

        item1Post.children.single { it.path == item2B.path }.children.apply {
            size shouldBe 1
            single { it.path == item3B.path }
        }
    }

    @Test
    fun `unnest all children`() {
        val item1 = ContentItem.fromInaccessible(LocalPath.build("folder1"))
        val item2A = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2A"))
        val item2B = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2B"))
        val item3A = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2A", "folder3"))
        val item3B = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2B", "folder3"))

        val preNesting = setOf(item1, item2A, item2B, item3A, item3B)
        val postNesting: Collection<ContentItem> = preNesting.toNestedContent()

        postNesting.toFlatContent().toSet() shouldBe preNesting
    }

    @Test
    fun `find child`() {
        val item1 = ContentItem.fromInaccessible(LocalPath.build("folder1"))
        val item2A = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2A"))
        val item2B = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2B"))
        val item3A = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2A", "folder3"))
        val item3B = ContentItem.fromInaccessible(LocalPath.build("folder1", "folder2B", "folder3"))

        val preNesting = setOf(item1, item2A, item2B, item3A, item3B)
        val postNesting: Collection<ContentItem> = preNesting.toNestedContent()

        postNesting.findContent {
            it.path == LocalPath.build("folder1", "folder2B", "folder3")
        } shouldBe item3B
    }

    private fun mockLookup(
        path: LocalPath,
        fileType: FileType = FileType.DIRECTORY,
        size: Long = 4096L,
    ) = mockk<LocalPathLookup> {
        coEvery { lookedUp } returns path
        coEvery { this@mockk.fileType } returns fileType
        coEvery { this@mockk.size } returns size
    }

    @Test
    fun `walkContentItem with default maxItems does not overflow`() = runTest {
        val path = LocalPath.build("test", "dir")
        val dirLookup = mockLookup(path)
        val childLookup = mockLookup(LocalPath.build("test", "dir", "file1"), FileType.FILE, 100L)

        val gatewaySwitch = mockk<GatewaySwitch> {
            coEvery { lookup(any(), type = any()) } returns dirLookup as APathLookup<APath>
            coEvery { walk(any(), any()) } returns flowOf(childLookup as APathLookup<APath>)
        }

        // Default maxItems=Int.MAX_VALUE must not crash with take(Int.MAX_VALUE + 1) overflow
        val result = path.walkContentItem(gatewaySwitch)
        result.path shouldBe path
        result.children.size shouldBe 1
    }

    @Test
    fun `walkContentItem falls back to du when maxItems exceeded`() = runTest {
        val path = LocalPath.build("test", "dir")
        val dirLookup = mockLookup(path)
        val child1 = mockLookup(LocalPath.build("test", "dir", "f1"), FileType.FILE, 100L)
        val child2 = mockLookup(LocalPath.build("test", "dir", "f2"), FileType.FILE, 200L)
        val child3 = mockLookup(LocalPath.build("test", "dir", "f3"), FileType.FILE, 300L)

        val gatewaySwitch = mockk<GatewaySwitch> {
            coEvery { lookup(any(), type = any()) } returns dirLookup as APathLookup<APath>
            coEvery { walk(any(), any()) } returns flowOf(
                child1 as APathLookup<APath>,
                child2 as APathLookup<APath>,
                child3 as APathLookup<APath>,
            )
            coEvery { du(any(), any()) } returns 600L
        }

        // maxItems=2 but 3 items exist, should fall back to sizeContentItem (du)
        val result = path.walkContentItem(gatewaySwitch, maxItems = 2)
        result.inaccessible shouldBe true
        result.itemSize shouldBe (4096L + 600L)  // lookup.size + du result
    }
}