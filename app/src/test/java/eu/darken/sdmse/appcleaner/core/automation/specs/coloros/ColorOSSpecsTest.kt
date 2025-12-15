package eu.darken.sdmse.appcleaner.core.automation.specs.coloros

import eu.darken.sdmse.appcleaner.core.automation.specs.BaseAppCleanerSpecTest
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.common.device.RomType
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.TestACSNodeInfo

class ColorOSSpecsTest : BaseAppCleanerSpecTest<ColorOSSpecs, ColorOSLabels>() {

    override val romType = RomType.COLOROS

    override fun createLabels(): ColorOSLabels = mockk()

    override fun createSpec() = ColorOSSpecs(
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        colorOSLabels = labels,
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

    @BeforeEach
    fun setupApiLevel() {
        // ColorOS requires API 26+
        mockkStatic("eu.darken.sdmse.common.BuildWrapKt")
        every { eu.darken.sdmse.common.hasApiLevel(any()) } returns true
    }

    @AfterEach
    fun cleanupApiLevel() {
        unmockkStatic("eu.darken.sdmse.common.BuildWrapKt")
    }

    // ============================================================
    // ColorOS-specific regression tests
    // ============================================================

    @Test
    fun `clear cache clicks directly on clicky button - pre API 35`() = runTest {
        // Before API 35, ColorOS requires isClickyButton()
        every { eu.darken.sdmse.common.hasApiLevel(35) } returns false

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=layout1, bounds=Rect(0, 0 - 1080, 400)
            ACS-DEBUG: --2: text='Clear cache', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button pkg=com.android.settings, identity=btn, bounds=Rect(50, 100 - 500, 150)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        val clearCacheButton = testRoot.crawl().first { it.node.text == "Clear cache" }.node as TestACSNodeInfo
        clearCacheButton.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `clear cache finds clickable parent when button not clickable - API 35+`() = runTest {
        // On API 35+, ColorOS finds non-clicky buttons and traverses to clickable parent
        // This pattern from actual device logs:
        // RelativeLayout(clickable) → Button(text="Clear cache", clickable=false)
        every { eu.darken.sdmse.common.hasApiLevel(35) } returns true

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=layout1, bounds=Rect(0, 0 - 1080, 400)
            ACS-DEBUG: --2: text='null', class=android.widget.RelativeLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/content_rl pkg=com.android.settings, identity=content_rl, bounds=Rect(0, 100 - 1080, 200)
            ACS-DEBUG: ---3: text='Clear cache', class=android.widget.Button, clickable=false, checkable=false enabled=true, id=com.android.settings:id/button pkg=com.android.settings, identity=btn, bounds=Rect(50, 110 - 500, 190)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // The clickable parent RelativeLayout should be clicked
        val clickableParent = testRoot.crawl().first { it.node.viewIdResourceName == "com.android.settings:id/content_rl" }.node as TestACSNodeInfo
        clickableParent.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }
}
