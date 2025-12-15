package eu.darken.sdmse.appcleaner.core.automation.specs.oneui

import eu.darken.sdmse.appcleaner.core.automation.specs.BaseAppCleanerSpecTest
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.common.device.RomType
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.TestACSNodeInfo

class OneUISpecsTest : BaseAppCleanerSpecTest<OneUISpecs, OneUILabels>() {

    override val romType = RomType.ONEUI

    override fun createLabels(): OneUILabels = mockk()

    override fun createSpec() = OneUISpecs(
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        oneUILabels = labels,
        storageEntryFinder = storageEntryFinder,
        generalSettings = generalSettings,
        stepper = stepper,
    )

    override fun mockLabelDefaults() {
        every { labels.getStorageEntryDynamic(any()) } returns emptySet()
        every { labels.getStorageEntryLabels(any()) } returns setOf("Storage")
        every { labels.getClearCacheDynamic(any()) } returns emptySet()
        every { labels.getClearCacheLabels(any()) } returns setOf("Clear cache")
    }

    // ============================================================
    // OneUI-specific tests
    // ============================================================

    @Test
    fun `clear cache finds button by label priority not tree order - Polish localization - GitHub 2046`() = runTest {
        // This test verifies the fix for GitHub issue #2046
        // In Polish: "Pamięć cache" = cache memory (label/header that appears first in tree)
        //           "Wyczyść pamięć podręczną" = clear cache (button that should be found)
        // Bug: tree-order search found "Pamięć cache" label before the actual button
        // Fix: label-priority search finds "Wyczyść pamięć podręczną" first because it's first in label list

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=layout1, bounds=Rect(0, 0 - 1080, 400)
            ACS-DEBUG: --2: text='Pamięć cache', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=cache_label, bounds=Rect(50, 100 - 500, 150)
            ACS-DEBUG: --2: text='128 MB', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/summary pkg=com.android.settings, identity=cache_size, bounds=Rect(50, 150 - 500, 200)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=layout2, bounds=Rect(0, 400 - 1080, 600)
            ACS-DEBUG: --2: text='Wyczyść pamięć podręczną', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button pkg=com.android.settings, identity=clear_btn, bounds=Rect(50, 450 - 500, 550)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        // Labels in priority order: clear cache button label first, then cache label
        every { labels.getClearCacheDynamic(any()) } returns emptySet()
        every { labels.getClearCacheLabels(any()) } returns setOf(
            "Wyczyść pamięć podręczną",  // Clear cache - should be found first (priority)
            "Pamięć cache",               // Cache memory - should NOT be found (lower priority)
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // Verify the correct button was clicked, not the cache label
        val clearCacheButton = testRoot.crawl().first { it.node.text == "Wyczyść pamięć podręczną" }.node as TestACSNodeInfo
        clearCacheButton.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `clear cache handles nested button text in OneUI 8_5+ style`() = runTest {
        // On newer One UI versions, the text "Wyczyść pamięć podręczną" is nested inside a non-clickable
        // TextView, and the clickable parent needs to be found

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button_container pkg=com.android.settings, identity=btn_container, bounds=Rect(0, 400 - 1080, 600)
            ACS-DEBUG: --2: text='Wyczyść pamięć podręczną', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=btn_text, bounds=Rect(50, 450 - 500, 550)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        every { labels.getClearCacheDynamic(any()) } returns emptySet()
        every { labels.getClearCacheLabels(any()) } returns setOf("Wyczyść pamięć podręczną")

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // The clickable parent container should have been clicked, not the text itself
        val buttonContainer = testRoot.crawl().first { it.node.viewIdResourceName == "com.android.settings:id/button_container" }.node as TestACSNodeInfo
        buttonContainer.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }
}
