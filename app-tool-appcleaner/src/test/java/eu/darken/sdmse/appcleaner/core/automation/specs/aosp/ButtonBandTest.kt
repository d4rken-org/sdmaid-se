package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The storage page here is reconstructed from a report on an Android 17 custom ROM (density ~2.75),
 * where both buttons are drawn between the header item (bottom 739) and the "space used" section
 * (top 1035) but are missing from the accessibility tree.
 */
class ButtonBandTest : BaseTest() {

    private fun node(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        className: String = LINEAR_LAYOUT,
        viewId: String? = null,
        kids: List<ACSNodeInfo?> = emptyList(),
    ): ACSNodeInfo = mockk<ACSNodeInfo>(relaxed = true).also { n ->
        every { n.className } returns className
        every { n.viewIdResourceName } returns viewId
        every { n.getScreenBounds() } returns ACSNodeInfo.ScreenBounds(left, top, right, bottom)
        every { n.childCount } returns kids.size
        every { n.getChild(any()) } answers { kids.getOrNull(firstArg()) }
        every { n.parent } returns null
        kids.filterNotNull().forEach { kid -> every { kid.parent } returns n }
    }

    private fun headerItem(
        anchorId: String? = ENTITY_HEADER_CONTENT_ID,
        extraKids: List<ACSNodeInfo> = emptyList(),
    ) = node(
        0, 234, 1080, 739,
        className = FRAME_LAYOUT,
        kids = listOf(
            node(
                42, 260, 1038, 700,
                viewId = anchorId,
                kids = listOf(
                    node(456, 290, 624, 458, className = IMAGE_VIEW),
                    node(84, 500, 996, 590, className = TEXT_VIEW),
                ),
            ),
        ) + extraKids,
    )

    private fun sectionHeader(top: Int = 1035) = node(0, top, 1080, top + 163)

    private fun sizeRow(top: Int) = node(
        0, top, 1080, top + 220,
        kids = listOf(
            node(168, top + 40, 900, top + 110, className = TEXT_VIEW, viewId = ROW_TITLE_ID),
            node(168, top + 110, 900, top + 180, className = TEXT_VIEW, viewId = ROW_SUMMARY_ID),
        ),
    )

    private fun pageItems(
        header: ACSNodeInfo = headerItem(),
        sectionTop: Int = 1035,
    ): List<ACSNodeInfo> = listOf(
        header,
        sectionHeader(sectionTop),
        sizeRow(sectionTop + 163),
        sizeRow(sectionTop + 383),
        sizeRow(sectionTop + 603),
        node(0, 2185, 1080, 2185),
    )

    private fun storageList(
        items: List<ACSNodeInfo?> = pageItems(),
        className: String = RECYCLER_VIEW,
        bottom: Int = 2400,
    ) = node(0, 234, 1080, bottom, className = className, kids = items)

    private fun window(vararg kids: ACSNodeInfo) = node(
        0, 0, 1080, 2400,
        className = FRAME_LAYOUT,
        kids = listOf(node(0, 0, 1080, 234, className = FRAME_LAYOUT)) + kids,
    )

    @Test
    fun `reconstructed report page has an empty button band`() {
        window(storageList()).hasEmptyButtonBand(MIN_GAP) shouldBe true
    }

    @Test
    fun `node starting inside the strip occupies it`() {
        val unlabeledRow = node(0, 760, 1080, 1000)

        window(storageList(), unlabeledRow).hasEmptyButtonBand(MIN_GAP) shouldBe false
    }

    @Test
    fun `zero-height node starting inside the strip occupies it`() {
        val items = pageItems().toMutableList().apply { add(1, node(0, 880, 1080, 880)) }

        window(storageList(items)).hasEmptyButtonBand(MIN_GAP) shouldBe false
    }

    @Test
    fun `button item starting right at the header bottom becomes the next item`() {
        val buttons = node(
            0, 739, 1080, 900,
            kids = listOf(
                node(42, 760, 520, 880, className = BUTTON),
                node(560, 760, 1038, 880, className = BUTTON),
            ),
        )
        val items = pageItems().toMutableList().apply { add(1, buttons) }

        window(storageList(items)).hasEmptyButtonBand(MIN_GAP) shouldBe false
    }

    @Test
    fun `empty gap below the list does not count when the strip is occupied`() {
        val buttons = node(0, 739, 1080, 1035)
        val items = listOf(headerItem(), buttons, sectionHeader())
        val footer = node(0, 2250, 1080, 2400)

        window(storageList(items, bottom = 1600), footer).hasEmptyButtonBand(MIN_GAP) shouldBe false
    }

    @Test
    fun `gap smaller than the minimum is rejected`() {
        val items = pageItems(sectionTop = 739 + MIN_GAP - 1)

        window(storageList(items)).hasEmptyButtonBand(MIN_GAP) shouldBe false
    }

    @Test
    fun `gap of exactly the minimum is accepted`() {
        val items = pageItems(sectionTop = 739 + MIN_GAP)

        window(storageList(items)).hasEmptyButtonBand(MIN_GAP) shouldBe true
    }

    @Test
    fun `missing anchor`() {
        val items = pageItems(header = headerItem(anchorId = null))

        window(storageList(items)).hasEmptyButtonBand(MIN_GAP) shouldBe false
    }

    @Test
    fun `no RecyclerView ancestor`() {
        window(storageList(className = "android.widget.ListView")).hasEmptyButtonBand(MIN_GAP) shouldBe false
    }

    @Test
    fun `no item below the header`() {
        window(storageList(listOf(headerItem()))).hasEmptyButtonBand(MIN_GAP) shouldBe false
    }

    @Test
    fun `non-positive minimum gap`() {
        window(storageList()).hasEmptyButtonBand(0) shouldBe false
    }

    @Test
    fun `list with a missing child`() {
        val items = listOf(headerItem(), null, sectionHeader())

        window(storageList(items)).hasEmptyButtonBand(MIN_GAP) shouldBe false
    }

    @Test
    fun `nodes below the anchor but inside the header item are ignored`() {
        val header = headerItem(extraKids = listOf(node(42, 705, 1038, 735, className = TEXT_VIEW)))

        window(storageList(pageItems(header = header))).hasEmptyButtonBand(MIN_GAP) shouldBe true
    }

    companion object {
        private const val MIN_GAP = 132
        private const val RECYCLER_VIEW = "androidx.recyclerview.widget.RecyclerView"
        private const val FRAME_LAYOUT = "android.widget.FrameLayout"
        private const val LINEAR_LAYOUT = "android.widget.LinearLayout"
        private const val TEXT_VIEW = "android.widget.TextView"
        private const val IMAGE_VIEW = "android.widget.ImageView"
        private const val BUTTON = "android.widget.Button"
    }
}
