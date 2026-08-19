package eu.darken.sdmse.main.core.shortcuts

import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.ShortcutConfig
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Ordering rules for the published launcher shortcuts. Launchers only display the first few entries,
 * so the order is the part the user actually feels, and it is derived from data (the stored
 * dashboard card order) that can be incomplete or duplicated.
 */
class ShortcutSelectionTest : BaseTest() {

    private val canonical = DashboardCardType.entries

    private fun ids(shortcuts: List<AppShortcut>) = shortcuts.map { it.id }

    @Test
    fun `the default config publishes the AppControl shortcut only`() {
        val shortcuts = buildShortcuts(
            oneTapEnabled = false,
            enabled = ShortcutConfig().tools,
            cardOrder = canonical,
            maxCount = 15,
        )

        ids(shortcuts) shouldBe listOf("appcontrol")
    }

    @Test
    fun `one-tap comes first, ahead of the tools`() {
        val shortcuts = buildShortcuts(
            oneTapEnabled = true,
            enabled = listOf(DashboardCardType.APPCONTROL, DashboardCardType.CORPSEFINDER),
            cardOrder = canonical,
            maxCount = 15,
        )

        ids(shortcuts) shouldBe listOf("onetap", "corpsefinder", "appcontrol")
    }

    @Test
    fun `tools follow the supplied card order, not the selection order`() {
        val shortcuts = buildShortcuts(
            oneTapEnabled = false,
            // Deliberately not in card order.
            enabled = listOf(DashboardCardType.STATS, DashboardCardType.CORPSEFINDER, DashboardCardType.APPCLEANER),
            cardOrder = canonical,
            maxCount = 15,
        )

        ids(shortcuts) shouldBe listOf("corpsefinder", "appcleaner", "stats")
    }

    @Test
    fun `a permuted card order permutes the shortcut order`() {
        // The end-to-end half of this (dragging a dashboard card) can't be driven by the UI test
        // harness, so the reordering guarantee lives here.
        val enabled = listOf(
            DashboardCardType.CORPSEFINDER,
            DashboardCardType.APPCLEANER,
            DashboardCardType.STATS,
        )
        val head = listOf(DashboardCardType.STATS, DashboardCardType.APPCLEANER, DashboardCardType.CORPSEFINDER)
        val permuted = head + (canonical - head.toSet())

        ids(buildShortcuts(false, enabled, canonical, 15)) shouldBe listOf("corpsefinder", "appcleaner", "stats")
        ids(buildShortcuts(false, enabled, permuted, 15)) shouldBe listOf("stats", "appcleaner", "corpsefinder")
    }

    @Test
    fun `a type missing from the card order still publishes, sorted last`() {
        // SWIPER isn't in the stored order at all. It must still get a shortcut, ranked after
        // everything the stored order does mention.
        val cardOrder = listOf(DashboardCardType.STATS, DashboardCardType.CORPSEFINDER)

        val shortcuts = buildShortcuts(
            oneTapEnabled = false,
            enabled = listOf(DashboardCardType.SWIPER, DashboardCardType.CORPSEFINDER, DashboardCardType.STATS),
            cardOrder = cardOrder,
            maxCount = 15,
        )

        ids(shortcuts) shouldBe listOf("stats", "corpsefinder", "swiper")
    }

    @Test
    fun `duplicate entries in the card order are collapsed to their first occurrence`() {
        val cardOrder = listOf(
            DashboardCardType.STATS,
            DashboardCardType.CORPSEFINDER,
            DashboardCardType.STATS,
        )

        normalizeCardOrder(cardOrder).size shouldBe canonical.size
        normalizeCardOrder(cardOrder).distinct().size shouldBe canonical.size
        normalizeCardOrder(cardOrder).take(2) shouldBe listOf(DashboardCardType.STATS, DashboardCardType.CORPSEFINDER)

        val shortcuts = buildShortcuts(
            oneTapEnabled = false,
            enabled = listOf(DashboardCardType.CORPSEFINDER, DashboardCardType.STATS),
            cardOrder = cardOrder,
            maxCount = 15,
        )
        ids(shortcuts) shouldBe listOf("stats", "corpsefinder")
    }

    @Test
    fun `a duplicated selection publishes each tool once`() {
        val shortcuts = buildShortcuts(
            oneTapEnabled = false,
            enabled = listOf(DashboardCardType.STATS, DashboardCardType.STATS),
            cardOrder = canonical,
            maxCount = 15,
        )

        ids(shortcuts) shouldBe listOf("stats")
    }

    @Test
    fun `the result is truncated to maxCount, keeping the highest ranked entries`() {
        val shortcuts = buildShortcuts(
            oneTapEnabled = true,
            enabled = canonical,
            cardOrder = canonical,
            maxCount = 3,
        )

        ids(shortcuts) shouldBe listOf("onetap", "corpsefinder", "systemcleaner")
    }

    @Test
    fun `nothing selected and one-tap off publishes nothing`() {
        buildShortcuts(
            oneTapEnabled = false,
            enabled = emptyList(),
            cardOrder = canonical,
            maxCount = 15,
        ) shouldBe emptyList()
    }

    @Test
    fun `resolveShortcutTool maps valid names and rejects absent or unknown ones`() {
        resolveShortcutTool("APPCLEANER") shouldBe DashboardCardType.APPCLEANER
        resolveShortcutTool("STATS") shouldBe DashboardCardType.STATS
        resolveShortcutTool(null) shouldBe null
        resolveShortcutTool("") shouldBe null
        resolveShortcutTool("appcleaner") shouldBe null
        resolveShortcutTool("NOT_A_TOOL") shouldBe null
    }
}
