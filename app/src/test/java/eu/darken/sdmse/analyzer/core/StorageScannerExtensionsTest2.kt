package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.storage.findContent
import eu.darken.sdmse.analyzer.core.storage.toFlatContent
import eu.darken.sdmse.analyzer.core.storage.toNestedContent
import eu.darken.sdmse.common.files.saf.SAFPath
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = TestApp::class)
class StorageScannerExtensionsTest2 : BaseTest() {

    private val baseTreeUri = "content://com.android.externalstorage.documents/tree/primary%3A"

    @Test
    fun `content item children nesting base variant 1`() {
        val item0 = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri))
        val item1 = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1"))
        val item2A = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2A"))
        val item2B = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2B"))
        val item3A = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2A", "folder3"))
        val item3B = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2B", "folder3"))

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
    fun `content item children nesting base variant 2`() {
        val item0 = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, ""))
        val item1 = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1"))
        val item2A = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2A"))
        val item2B = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2B"))
        val item3A = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2A", "folder3"))
        val item3B = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2B", "folder3"))

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
        val item1 = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1"))
        val item2A = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2A"))
        val item2B = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2B"))
        val item3A = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2A", "folder3"))
        val item3B = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2B", "folder3"))

        val preNesting = setOf(item1, item2A, item2B, item3A, item3B)
        val postNesting: Collection<ContentItem> = preNesting.toNestedContent()

        postNesting.toFlatContent().toSet() shouldBe preNesting
    }

    @Test
    fun `find child`() {
        val item1 = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1"))
        val item2A = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2A"))
        val item2B = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2B"))
        val item3A = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2A", "folder3"))
        val item3B = ContentItem.fromInaccessible(SAFPath.build(baseTreeUri, "folder1", "folder2B", "folder3"))

        val preNesting = setOf(item1, item2A, item2B, item3A, item3B)
        val postNesting: Collection<ContentItem> = preNesting.toNestedContent()

        postNesting.findContent {
            it.path == SAFPath.build(baseTreeUri, "folder1", "folder2B", "folder3")
        } shouldBe item3B
    }
}