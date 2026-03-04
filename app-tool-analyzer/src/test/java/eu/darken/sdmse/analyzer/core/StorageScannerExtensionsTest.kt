package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.storage.findContent
import eu.darken.sdmse.analyzer.core.storage.toFlatContent
import eu.darken.sdmse.analyzer.core.storage.toNestedContent
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
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
}