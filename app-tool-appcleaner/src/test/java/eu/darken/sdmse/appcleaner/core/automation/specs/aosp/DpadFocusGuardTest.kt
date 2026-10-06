package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DpadFocusGuardTest : BaseTest() {

    private fun node(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        viewId: String? = null,
        pkg: String? = SETTINGS,
    ): ACSNodeInfo = mockk<ACSNodeInfo>(relaxed = true).also { n ->
        every { n.viewIdResourceName } returns viewId
        every { n.packageName } returns pkg
        every { n.getScreenBounds() } returns ACSNodeInfo.ScreenBounds(left, top, right, bottom)
    }

    private fun anchor() = node(42, 300, 1038, 702, viewId = ENTITY_HEADER_CONTENT_ID)

    @Test
    fun `anchor itself is not outside`() {
        val anchor = anchor()

        anchor.isOutsideButtonRow(anchor) shouldBe false
    }

    @Test
    fun `toolbar button entirely above the header is outside`() {
        node(948, 120, 1080, 264).isOutsideButtonRow(anchor()) shouldBe true
    }

    @Test
    fun `node ending exactly at the header top is outside`() {
        node(0, 120, 132, 300).isOutsideButtonRow(anchor()) shouldBe true
    }

    @Test
    fun `node of another package is outside`() {
        node(42, 760, 520, 880, pkg = "com.android.launcher3").isOutsideButtonRow(anchor()) shouldBe true
    }

    @Test
    fun `node overlapping the header is not outside`() {
        node(84, 250, 996, 400).isOutsideButtonRow(anchor()) shouldBe false
    }

    @Test
    fun `node below the header is not outside`() {
        node(42, 760, 520, 880).isOutsideButtonRow(anchor()) shouldBe false
    }

    @Test
    fun `focused node with empty bounds is not outside`() {
        node(948, 120, 948, 120).isOutsideButtonRow(anchor()) shouldBe false
    }

    @Test
    fun `anchor with empty bounds is not outside`() {
        val anchor = node(42, 300, 42, 300, viewId = ENTITY_HEADER_CONTENT_ID)

        node(948, 120, 1080, 264).isOutsideButtonRow(anchor) shouldBe false
    }

    companion object {
        private const val SETTINGS = "com.android.settings"
    }
}
