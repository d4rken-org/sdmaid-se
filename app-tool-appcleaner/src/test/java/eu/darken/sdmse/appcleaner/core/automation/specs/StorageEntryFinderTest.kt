package eu.darken.sdmse.appcleaner.core.automation.specs

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Regression coverage for the Samsung One UI 6 Mobile-data false-positive that the
 * size-based fallback in [StorageEntryFinder.storageFinderAOSP] can produce when the
 * App Info screen happens to expose a non-Storage row whose summary contains the same
 * size text as the app's on-disk size.
 *
 * The bug: on a Galaxy A52 5G the Storage row is structurally absent from the
 * accessibility tree, and the Mobile data row's summary "0.97 GB used since Feb 1"
 * collides with the Threads app's 0.97 GB on-disk size, so the finder taps Mobile data
 * instead of Storage.
 *
 * These tests target the [hasRowTitleIn] anti-label helper, which guards the size
 * fallback against rows whose title we can prove is not Storage.
 */
class StorageEntryFinderTest : BaseTest() {

    /**
     * Build a mock row: clickable LinearLayout containing two TextView children
     * (android:id/title with [titleText], android:id/summary with [summaryText]).
     * Returns (row, titleNode, summaryNode) so the caller can use the summary as the
     * anti-label query target.
     */
    private fun buildRow(
        titleText: String,
        summaryText: String,
        nestTitleExtraLevel: Boolean = false,
    ): Triple<ACSNodeInfo, ACSNodeInfo, ACSNodeInfo> {
        val title = mockk<ACSNodeInfo>(relaxed = true).also {
            every { it.text } returns titleText
            every { it.viewIdResourceName } returns "android:id/title"
            every { it.className } returns "android.widget.TextView"
            every { it.childCount } returns 0
            every { it.getChild(any()) } returns null
            every { it.isClickable } returns false
        }
        val summary = mockk<ACSNodeInfo>(relaxed = true).also {
            every { it.text } returns summaryText
            every { it.viewIdResourceName } returns "android:id/summary"
            every { it.className } returns "android.widget.TextView"
            every { it.childCount } returns 0
            every { it.getChild(any()) } returns null
            every { it.isClickable } returns false
        }

        val titleHolder: ACSNodeInfo = if (nestTitleExtraLevel) {
            mockk<ACSNodeInfo>(relaxed = true).also {
                every { it.viewIdResourceName } returns null
                every { it.className } returns "android.widget.LinearLayout"
                every { it.childCount } returns 1
                every { it.getChild(0) } returns title
                every { it.getChild(neq(0)) } returns null
                every { it.text } returns null
                every { it.isClickable } returns false
                every { title.parent } returns it
            }
        } else {
            title
        }

        val row = mockk<ACSNodeInfo>(relaxed = true).also {
            every { it.viewIdResourceName } returns null
            every { it.className } returns "android.widget.LinearLayout"
            every { it.isClickable } returns true
            every { it.childCount } returns 2
            every { it.getChild(0) } returns titleHolder
            every { it.getChild(1) } returns summary
            every { it.getChild(match { idx -> idx >= 2 }) } returns null
            every { it.text } returns null
            every { titleHolder.parent } returns it
            every { summary.parent } returns it
        }
        return Triple(row, title, summary)
    }

    @Test
    fun `anti-label list is empty - size match survives (bug repros for other OEMs)`() {
        val (_, _, summary) = buildRow(
            titleText = "Mobile data",
            summaryText = "0.97 GB used since Feb 1",
        )

        summary.hasRowTitleIn(emptySet()) shouldBe false
    }

    @Test
    fun `mobile data row is rejected when anti-label is configured`() {
        val (_, _, summary) = buildRow(
            titleText = "Mobile data",
            summaryText = "0.97 GB used since Feb 1",
        )

        summary.hasRowTitleIn(setOf("Mobile data", "Wi-Fi data")) shouldBe true
    }

    @Test
    fun `wifi data row is also rejected`() {
        val (_, _, summary) = buildRow(
            titleText = "Wi-Fi data",
            summaryText = "0.97 GB used since Feb 1",
        )

        summary.hasRowTitleIn(setOf("Mobile data", "Wi-Fi data")) shouldBe true
    }

    @Test
    fun `storage row is not rejected even when anti-labels are configured`() {
        val (_, _, summary) = buildRow(
            titleText = "Storage",
            summaryText = "0.97 GB",
        )

        summary.hasRowTitleIn(setOf("Mobile data", "Wi-Fi data")) shouldBe false
    }

    @Test
    fun `nested title still triggers rejection`() {
        val (_, _, summary) = buildRow(
            titleText = "Mobile data",
            summaryText = "0.97 GB used since Feb 1",
            nestTitleExtraLevel = true,
        )

        summary.hasRowTitleIn(setOf("Mobile data")) shouldBe true
    }

    @Test
    fun `title in unrelated locale is not rejected`() {
        val (_, _, summary) = buildRow(
            titleText = "Battery",
            summaryText = "0.97 GB used since last fully charged",
        )

        summary.hasRowTitleIn(setOf("Mobile data", "Wi-Fi data")) shouldBe false
    }

    @Test
    fun `no clickable ancestor leaves node unaffected`() {
        val standalone = mockk<ACSNodeInfo>(relaxed = true).also {
            every { it.text } returns "0.97 GB used since Feb 1"
            every { it.viewIdResourceName } returns "android:id/summary"
            every { it.className } returns "android.widget.TextView"
            every { it.parent } returns null
            every { it.childCount } returns 0
            every { it.getChild(any()) } returns null
        }

        standalone.hasRowTitleIn(setOf("Mobile data")) shouldBe false
    }
}
