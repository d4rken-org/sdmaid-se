package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Covers reading the value out of a Settings storage row.
 *
 * The shapes here are taken from real accessibility dumps: the flat one from the Motorola Hello UI
 * (Android 17) report that motivated this, the nested one from the Samsung layout already modelled
 * in StorageEntryFinderTest, where the title sits one level deeper than the summary.
 */
class StorageRowReaderTest : BaseTest() {

    private fun textNode(viewId: String?, text: String?) = mockk<ACSNodeInfo>(relaxed = true).also {
        every { it.viewIdResourceName } returns viewId
        every { it.text } returns text
        every { it.childCount } returns 0
        every { it.getChild(any()) } returns null
    }

    private fun container(vararg kids: ACSNodeInfo) = mockk<ACSNodeInfo>(relaxed = true).also { parent ->
        every { parent.viewIdResourceName } returns null
        every { parent.text } returns null
        every { parent.childCount } returns kids.size
        every { parent.getChild(any()) } answers { kids.getOrNull(firstArg()) }
        kids.forEach { kid -> every { kid.parent } returns parent }
    }

    @Test
    fun `reads the value from a flat row`() {
        val title = textNode(ROW_TITLE_ID, "Cache")
        val summary = textNode(ROW_SUMMARY_ID, "0 byte")
        container(title, summary)

        title.findRowSummaryText() shouldBe "0 byte"
    }

    @Test
    fun `reads the value when the title is nested one level deeper`() {
        val title = textNode(ROW_TITLE_ID, "Cache")
        val titleHolder = container(title)
        val summary = textNode(ROW_SUMMARY_ID, "0 byte")
        container(titleHolder, summary)

        title.findRowSummaryText() shouldBe "0 byte"
    }

    @Test
    fun `gives up rather than reaching further than allowed`() {
        val title = textNode(ROW_TITLE_ID, "Cache")
        val inner = container(title)
        val middle = container(inner)
        val summary = textNode(ROW_SUMMARY_ID, "0 byte")
        container(middle, summary)

        title.findRowSummaryText() shouldBe null
    }

    @Test
    fun `returns null when the row has no value`() {
        val title = textNode(ROW_TITLE_ID, "Cache")
        container(title)

        title.findRowSummaryText() shouldBe null
    }

    @Test
    fun `returns null when the title has no parent at all`() {
        val title = textNode(ROW_TITLE_ID, "Cache").also { every { it.parent } returns null }

        title.findRowSummaryText() shouldBe null
    }

    @Test
    fun `does not pick up a neighbouring row's value`() {
        val title = textNode(ROW_TITLE_ID, "Cache")
        val ownRow = container(title)
        val otherTitle = textNode(ROW_TITLE_ID, "Total")
        val otherSummary = textNode(ROW_SUMMARY_ID, "17,35 MB")
        val otherRow = container(otherTitle, otherSummary)
        container(ownRow, otherRow)

        title.findRowSummaryText() shouldBe null
    }
}
