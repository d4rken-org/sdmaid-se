package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.storage.toNesting
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StorageScannerTest : BaseTest() {

    @Test
    fun `content item children nesting`() {
        val item1 = ContentItem.fromInaccessible(LocalPath.build("folder1"))
        val item2A = ContentItem.fromInaccessible(LocalPath.build("folder1/folder2A"))
        val item2B = ContentItem.fromInaccessible(LocalPath.build("folder1/folder2B"))
        val item3A = ContentItem.fromInaccessible(LocalPath.build("folder1/folder2A/folder3"))
        val item3B = ContentItem.fromInaccessible(LocalPath.build("folder1/folder2B/folder3"))

        val preNesting = setOf(item1, item2A, item2B, item3A, item3B)
        val postNesting = preNesting.toNesting()

        val item1Post = postNesting.single()
        item1Post.path shouldBe item1.path

        item1Post.children!!.apply {
            size shouldBe 2

            single { it.path == item2B.path }
        }

        item1Post.children!!.single { it.path == item2A.path }.children!!.apply {
            size shouldBe 1
            single { it.path == item3A.path }
        }

        item1Post.children!!.single { it.path == item2B.path }.children!!.apply {
            size shouldBe 1
            single { it.path == item3B.path }
        }
    }
}