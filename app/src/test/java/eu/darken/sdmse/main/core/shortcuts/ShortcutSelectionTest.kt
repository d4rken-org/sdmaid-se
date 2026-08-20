package eu.darken.sdmse.main.core.shortcuts

import eu.darken.sdmse.main.core.SDMTool
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ShortcutSelectionTest : BaseTest() {

    private fun ids(shortcuts: List<AppShortcut>) = shortcuts.map { it.id }

    @Test
    fun `only AppControl is published when nothing is opted in`() {
        ids(buildShortcuts(oneTapEnabled = false, cleanTools = emptySet(), maxCount = 10)) shouldBe
                listOf("appcontrol")
    }

    @Test
    fun `OneTap comes first when enabled, then AppControl`() {
        ids(buildShortcuts(oneTapEnabled = true, cleanTools = emptySet(), maxCount = 10)) shouldBe
                listOf("onetap", "appcontrol")
    }

    @Test
    fun `clean actions follow the fixed tools and keep the one-click order`() {
        val shortcuts = buildShortcuts(
            oneTapEnabled = true,
            // Deliberately out of order: the published order comes from ONECLICK_TYPES, not the input.
            cleanTools = listOf(
                SDMTool.Type.DEDUPLICATOR,
                SDMTool.Type.APPCLEANER,
                SDMTool.Type.SYSTEMCLEANER,
                SDMTool.Type.CORPSEFINDER,
            ),
            maxCount = 10,
        )

        ids(shortcuts) shouldBe listOf(
            "onetap",
            "appcontrol",
            "clean_corpsefinder",
            "clean_systemcleaner",
            "clean_appcleaner",
            "clean_deduplicator",
        )
    }

    @Test
    fun `only the opted-in clean actions are published`() {
        val shortcuts = buildShortcuts(
            oneTapEnabled = false,
            cleanTools = setOf(SDMTool.Type.APPCLEANER, SDMTool.Type.CORPSEFINDER),
            maxCount = 10,
        )

        ids(shortcuts) shouldBe listOf("appcontrol", "clean_corpsefinder", "clean_appcleaner")
    }

    @Test
    fun `a tool without a one-click task can never be selected`() {
        val shortcuts = buildShortcuts(
            oneTapEnabled = false,
            cleanTools = setOf(SDMTool.Type.ANALYZER, SDMTool.Type.SWIPER),
            maxCount = 10,
        )

        ids(shortcuts) shouldBe listOf("appcontrol")
    }

    @Test
    fun `the device cap truncates from the end`() {
        val all = buildShortcuts(
            oneTapEnabled = true,
            cleanTools = OneTapCleaner.ONECLICK_TYPES,
            maxCount = Int.MAX_VALUE,
        )
        all.size shouldBe 6

        ids(buildShortcuts(true, OneTapCleaner.ONECLICK_TYPES, maxCount = 3)) shouldBe
                listOf("onetap", "appcontrol", "clean_corpsefinder")
        // A nonsensical device limit must not throw out of take().
        buildShortcuts(true, OneTapCleaner.ONECLICK_TYPES, maxCount = -1) shouldBe emptyList()
    }
}
