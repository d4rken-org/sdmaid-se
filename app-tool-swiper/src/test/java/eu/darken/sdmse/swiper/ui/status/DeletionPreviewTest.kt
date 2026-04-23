package eu.darken.sdmse.swiper.ui.status

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DeletionPreviewTest : BaseTest() {

    private fun lookup(path: APath, size: Long): APathLookup<APath> = mockk(relaxed = true) {
        every { segments } returns path.segments
        every { this@mockk.size } returns size
        every { lookedUp } returns path
    }

    private fun item(id: Long, path: APath, size: Long, decision: SwipeDecision): SwipeItem =
        SwipeItem(id = id, sessionId = "s", itemIndex = id.toInt(), lookup = lookup(path, size), decision = decision)

    @Test
    fun `empty items produces empty preview`() {
        val preview = DeletionPreview.from(emptyList(), listOf(LocalPath.build("storage", "emulated", "0")))
        preview.buckets shouldBe emptyList()
        preview.moreFolders shouldBe 0
    }

    @Test
    fun `groups items by first relative segment under source`() {
        val source = LocalPath.build("storage", "emulated", "0")
        val items = listOf(
            item(1, LocalPath.build("storage", "emulated", "0", "DCIM", "a.jpg"), 100, SwipeDecision.DELETE),
            item(2, LocalPath.build("storage", "emulated", "0", "DCIM", "sub", "b.jpg"), 200, SwipeDecision.DELETE),
            item(3, LocalPath.build("storage", "emulated", "0", "Download", "c.pdf"), 50, SwipeDecision.DELETE),
        )
        val preview = DeletionPreview.from(items, listOf(source))
        preview.buckets.map { it.label } shouldBe listOf("DCIM", "Download")
        preview.buckets.first { it.label == "DCIM" }.count shouldBe 2
        preview.buckets.first { it.label == "DCIM" }.size shouldBe 300
        preview.buckets.first { it.label == "Download" }.count shouldBe 1
        preview.moreFolders shouldBe 0
    }

    @Test
    fun `includes DELETE_FAILED alongside DELETE`() {
        val source = LocalPath.build("root")
        val items = listOf(
            item(1, LocalPath.build("root", "A", "file"), 10, SwipeDecision.DELETE),
            item(2, LocalPath.build("root", "A", "file2"), 20, SwipeDecision.DELETE_FAILED),
            item(3, LocalPath.build("root", "A", "file3"), 30, SwipeDecision.KEEP),
            item(4, LocalPath.build("root", "A", "file4"), 40, SwipeDecision.DELETED),
            item(5, LocalPath.build("root", "A", "file5"), 50, SwipeDecision.UNDECIDED),
        )
        val preview = DeletionPreview.from(items, listOf(source))
        preview.buckets.single().count shouldBe 2
        preview.buckets.single().size shouldBe 30
    }

    @Test
    fun `sorts buckets by size descending and caps at 5`() {
        val source = LocalPath.build("root")
        val items = (1..7).map { i ->
            item(i.toLong(), LocalPath.build("root", "folder$i", "file"), i * 100L, SwipeDecision.DELETE)
        }
        val preview = DeletionPreview.from(items, listOf(source))
        preview.buckets.map { it.label } shouldBe listOf("folder7", "folder6", "folder5", "folder4", "folder3")
        preview.moreFolders shouldBe 2
    }

    @Test
    fun `multi-root session picks longest matching source path`() {
        val broad = LocalPath.build("storage")
        val narrow = LocalPath.build("storage", "emulated", "0")
        val items = listOf(
            item(1, LocalPath.build("storage", "emulated", "0", "Downloads", "a"), 10, SwipeDecision.DELETE),
            item(2, LocalPath.build("storage", "other", "Pictures", "a"), 20, SwipeDecision.DELETE),
        )
        val preview = DeletionPreview.from(items, listOf(broad, narrow))
        val labels = preview.buckets.map { it.label }.toSet()
        labels shouldBe setOf("Downloads", "other")
    }

    @Test
    fun `items directly under source are omitted — only folder buckets appear`() {
        val source = LocalPath.build("root")
        val items = listOf(
            item(1, LocalPath.build("root", "direct.txt"), 10, SwipeDecision.DELETE),
            item(2, LocalPath.build("root", "Folder", "x"), 20, SwipeDecision.DELETE),
        )
        val preview = DeletionPreview.from(items, listOf(source))
        preview.buckets.map { it.label } shouldBe listOf("Folder")
        preview.buckets.single().count shouldBe 1
    }

    @Test
    fun `items outside any source are omitted`() {
        val source = LocalPath.build("root")
        val items = listOf(
            item(1, LocalPath.build("unrelated", "thing"), 10, SwipeDecision.DELETE),
        )
        val preview = DeletionPreview.from(items, listOf(source))
        preview.buckets shouldBe emptyList()
    }
}
