package eu.darken.sdmse.appcleaner.core.automation.specs.oxygenos

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

class OxygenOSSpecsTest : BaseAppCleanerSpecTest<OxygenOSSpecs, OxygenOSLabels>() {

    override val romType = RomType.OXYGENOS

    override fun createLabels(): OxygenOSLabels = mockk()

    override fun createSpec() = OxygenOSSpecs(
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        oxygenOSLabels = labels,
        storageEntryFinder = storageEntryFinder,
        generalSettings = generalSettings,
        stepper = stepper,
    )

    override fun mockLabelDefaults() {
        every { labels.getStorageEntryDynamic(any()) } returns emptySet()
        every { labels.getStorageEntryLabels(any()) } returns setOf("Storage")
        every { labels.getClearCacheDynamic(any()) } returns emptySet()
        every { labels.getClearCacheStatic(any()) } returns setOf("Clear cache")
    }

    @BeforeEach
    fun setupApiLevel() {
        mockkStatic("eu.darken.sdmse.common.BuildWrapKt")
        every { eu.darken.sdmse.common.hasApiLevel(any()) } returns true
    }

    @AfterEach
    fun cleanupApiLevel() {
        unmockkStatic("eu.darken.sdmse.common.BuildWrapKt")
    }

    // ============================================================
    // OxygenOS-specific regression tests
    // ============================================================

    @Test
    fun `clear cache clicks directly on clicky button - pre API 34`() = runTest {
        // Before API 34, OxygenOS requires isClickyButton()
        every { eu.darken.sdmse.common.hasApiLevel(34) } returns false

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
    fun `clear cache finds clickable parent when button not clickable - API 34+`() = runTest {
        // On API 34+, OxygenOS finds non-clicky buttons and traverses to clickable parent
        every { eu.darken.sdmse.common.hasApiLevel(34) } returns true

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

    @Test
    fun `clear cache handles disabled button when cache is 0 bytes - API 34+ - GitHub 1889`() = runTest {
        // On API 34+, when cache is 0, the clear cache button AND all parents are disabled.
        // The automation should detect this and return success (nothing to clear).
        // See: https://github.com/d4rken-org/sdmaid-se/issues/1889
        every { eu.darken.sdmse.common.hasApiLevel(34) } returns true

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=layout1, bounds=Rect(0, 0 - 1080, 400)
            ACS-DEBUG: --2: text='null', class=android.widget.RelativeLayout, clickable=false, checkable=false enabled=false, id=com.android.settings:id/content_rl pkg=com.android.settings, identity=content_rl, bounds=Rect(0, 100 - 1080, 200)
            ACS-DEBUG: ---3: text='Clear cache', class=android.widget.Button, clickable=false, checkable=false enabled=false, id=com.android.settings:id/button pkg=com.android.settings, identity=btn, bounds=Rect(50, 110 - 500, 190)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        val result = captureAndRunClearCacheAction()

        // Should return true (success) - disabled button with no cache means nothing to clear
        result shouldBe true
    }
}
