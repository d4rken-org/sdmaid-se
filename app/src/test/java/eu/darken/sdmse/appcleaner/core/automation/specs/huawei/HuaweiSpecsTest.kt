package eu.darken.sdmse.appcleaner.core.automation.specs.huawei

import eu.darken.sdmse.appcleaner.core.automation.specs.BaseAppCleanerSpecTest
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.common.device.RomType
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.TestACSNodeInfo
import testhelpers.mockDataStoreValue

class HuaweiSpecsTest : BaseAppCleanerSpecTest<HuaweiSpecs, HuaweiLabels>() {

    override val romType = RomType.HUAWEI

    override fun createLabels(): HuaweiLabels = mockk()

    override fun createSpec() = HuaweiSpecs(
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        huaweiLabels = labels,
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
        // Mock hasApiLevel to return true for API 29+ since HuaweiSpecs requires it
        mockkStatic("eu.darken.sdmse.common.BuildWrapKt")
        every { eu.darken.sdmse.common.hasApiLevel(any()) } returns true
    }

    @AfterEach
    fun cleanupApiLevel() {
        unmockkStatic("eu.darken.sdmse.common.BuildWrapKt")
    }

    // Override tests that need API level mocking - Huawei requires API 29+
    @Test
    override fun `isResponsible returns true when AUTO and device matches`() = runTest {
        every { generalSettings.romTypeDetection } returns mockDataStoreValue(RomType.AUTO)
        coEvery { deviceDetective.getROMType() } returns romType

        val spec = createSpec()
        val result = spec.isResponsible(createTestPkg())

        result shouldBe true
    }

    // ============================================================
    // Huawei-specific tests
    // ============================================================

    @Test
    fun `clear cache requires isClickyButton - clicks directly on Button with text`() = runTest {
        // Huawei ONLY supports isClickyButton() which requires both isClickable=true AND className=Button
        // It does NOT traverse to find clickable parents like AOSP/OneUI do

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=layout1, bounds=Rect(0, 0 - 1080, 400)
            ACS-DEBUG: --2: text='Clear cache', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/clear_cache_btn pkg=com.android.settings, identity=btn, bounds=Rect(50, 100 - 500, 150)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        val clearCacheButton = testRoot.crawl().first { it.node.text == "Clear cache" }.node as TestACSNodeInfo
        clearCacheButton.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `clear cache returns false when text is not in clickyButton - Huawei limitation`() = runTest {
        // Huawei does NOT support findClickableParent - it only finds isClickyButton() nodes
        // If the text is in a TextView (not a Button), Huawei cannot click it

        val clearCacheText = TestACSNodeInfo(text = "Clear cache", isClickable = false, isEnabled = true)
        val clickableParent = TestACSNodeInfo(isClickable = true, isEnabled = true).addChild(clearCacheText)
        testRoot = TestACSNodeInfo().addChild(clickableParent)

        val result = captureAndRunClearCacheAction()

        // Huawei requires isClickyButton() which needs BOTH isClickable=true AND className=Button
        // Since the text node is not a clicky button, Huawei returns false
        result shouldBe false
    }
}
