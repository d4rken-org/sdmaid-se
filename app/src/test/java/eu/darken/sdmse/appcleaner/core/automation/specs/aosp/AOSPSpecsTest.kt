package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import android.accessibilityservice.AccessibilityService
import android.content.res.Resources
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowMetrics
import eu.darken.sdmse.appcleaner.core.automation.specs.BaseAppCleanerSpecTest
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.errors.isAutomationUnusable
import eu.darken.sdmse.automation.core.input.InputInjector
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.ui.SizeParser
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.TestACSNodeInfo
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds

class AOSPSpecsTest : BaseAppCleanerSpecTest<AOSPSpecs, AOSPLabels>() {

    override val romType = RomType.AOSP

    private val inputInjector: InputInjector = mockk<InputInjector>().apply {
        coEvery { canInject() } returns false
    }

    // No cache stats by default, so presses are verified by the storage page.
    private val cacheProvider: InaccessibleCacheProvider = mockk<InaccessibleCacheProvider>().apply {
        coEvery { determineCache(any()) } returns null
    }

    override fun createLabels(): AOSPLabels = mockk()

    override fun createSpec() = AOSPSpecs(
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        aospLabels = labels,
        storageEntryFinder = storageEntryFinder,
        romTypeProvider = romTypeProvider,
        stepper = stepper,
        inputInjector = inputInjector,
        inaccessibleCacheProvider = cacheProvider,
    )

    override fun mockLabelDefaults() {
        every { labels.getStorageEntryDynamic(any()) } returns emptySet()
        every { labels.getStorageEntryStatic(any()) } returns setOf("Storage")
        every { labels.getComputingSizeDynamic(any()) } returns emptySet()
        every { labels.getClearCacheDynamic(any()) } returns emptySet()
        every { labels.getClearCacheStatic(any()) } returns setOf("Clear cache")
        every { labels.getCacheSizeLabelDynamic(any()) } returns emptySet()
        every { labels.getCacheSizeLabelStatic(any()) } returns setOf("Cache")
    }

    @BeforeEach
    fun aospSetup() {
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "TestOEM"
        every { BuildWrap.PRODUCT } returns "test_product"
    }

    @AfterEach
    fun cleanup() {
        unmockkStatic(::hasApiLevel)
        unmockkObject(BuildWrap)
        unmockkConstructor(SizeParser::class)
    }

    private fun emitValidationEventAsync(pkgId: String = "com.android.settings") {
        testHost.scope.launch {
            delay(1)
            testHost.emitEvent(pkgId, android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    private fun stubDensity(density: Float = 2.75f) {
        every { testHost.service.resources.displayMetrics } returns DisplayMetrics().apply { this.density = density }
    }

    // Storage page with the clear-cache button row withheld from the tree: nothing starts in the
    // 296px strip between the header item (bottom 739) and the next item (top 1035), unless
    // [stripRow] puts an unlabeled clickable row there. [header] = false drops entity_header_content.
    private fun buttonBandTree(
        cacheSummary: String = "143 kB",
        stripRow: Boolean = false,
        header: Boolean = true,
    ): TestACSNodeInfo {
        val stripLine = if (stripRow) {
            "ACS-DEBUG: --2: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=null pkg=com.android.settings, identity=stripRow, bounds=Rect(0, 760 - 1080, 1000)\n"
        } else {
            ""
        }
        val headerLine = if (header) {
            "ACS-DEBUG: ---3: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(42, 260 - 1038, 700)\n"
        } else {
            ""
        }
        return buildTestTree(
            "ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)\n" +
                "ACS-DEBUG: -1: text='null', class=androidx.recyclerview.widget.RecyclerView, clickable=false, checkable=false enabled=true, id=com.android.settings:id/recycler_view pkg=com.android.settings, identity=list, bounds=Rect(0, 234 - 1080, 2400)\n" +
                "ACS-DEBUG: --2: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=headerItem, bounds=Rect(0, 234 - 1080, 739)\n" +
                headerLine +
                stripLine +
                "ACS-DEBUG: --2: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=nextItem, bounds=Rect(0, 1035 - 1080, 1198)\n" +
                "ACS-DEBUG: ---3: text='App size', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=sizeTitle, bounds=Rect(126, 1077 - 600, 1156)\n" +
                "ACS-DEBUG: --2: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=cacheRow, bounds=Rect(0, 1198 - 1080, 1361)\n" +
                "ACS-DEBUG: ---3: text='Cache', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=rowTitle, bounds=Rect(126, 1240 - 600, 1319)\n" +
                "ACS-DEBUG: ---3: text='$cacheSummary', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/summary pkg=com.android.settings, identity=rowSummary, bounds=Rect(700, 1240 - 1038, 1319)\n" +
                "ACS-DEBUG: --2: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=footer, bounds=Rect(0, 2185 - 1080, 2185)",
            withBounds = true,
        )
    }

    // ============================================================
    // AOSP-specific regression tests
    // ============================================================

    @Test
    fun `clear cache clicks directly on clicky button - standard pattern`() = runTest {
        // Standard AOSP pattern: Clear cache is a Button with clickable=true
        // ----------10: text='null', class=android.widget.LinearLayout
        // -----------11: text='Clear cache', class=android.widget.Button, clickable=true

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=layout1, bounds=Rect(0, 0 - 1080, 400)
            ACS-DEBUG: --2: text='Clear storage', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button1 pkg=com.android.settings, identity=btn1, bounds=Rect(50, 100 - 500, 150)
            ACS-DEBUG: --2: text='Clear cache', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button2 pkg=com.android.settings, identity=btn2, bounds=Rect(550, 100 - 1000, 150)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        val clearCacheButton = testRoot.crawl().first { it.node.text == "Clear cache" }.node as TestACSNodeInfo
        clearCacheButton.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `clear cache finds clickable parent when button is LinearLayout - Android 16 - GitHub 1794`() = runTest {
        // Android 16 Beta pattern: Clear cache is in a non-clickable TextView,
        // but parent LinearLayout is clickable
        // -----------11: text='null', class=android.widget.LinearLayout, clickable=true, id=com.android.settings:id/action2
        // ------------12: text='null', class=android.widget.Button, clickable=true, id=com.android.settings:id/button2
        // ------------12: text='Clear cache', class=android.widget.TextView, clickable=true, id=com.android.settings:id/text2

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/action2 pkg=com.android.settings, identity=action2, bounds=Rect(540, 959 - 1020, 1239)
            ACS-DEBUG: --2: text='null', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button2 pkg=com.android.settings, identity=btn2, bounds=Rect(691, 959 - 869, 1098)
            ACS-DEBUG: --2: text='Clear cache', class=android.widget.TextView, clickable=true, checkable=false enabled=true, id=com.android.settings:id/text2 pkg=com.android.settings, identity=text2, bounds=Rect(628, 1113 - 931, 1181)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // The clickable parent LinearLayout should be clicked, not the TextView
        val clickableParent = testRoot.crawl()
            .first { it.node.viewIdResourceName == "com.android.settings:id/action2" }.node as TestACSNodeInfo
        clickableParent.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `clear cache finds clickable sibling when text is not in button - Spanish localization`() = runTest {
        // Some AOSP variants: Text "Borrar caché" is in non-clickable TextView,
        // but has a clickable Button sibling
        //-----------11: text='null', class=android.widget.LinearLayout, clickable=false, id=com.android.settings:id/action2
        //------------12: text='null', class=android.widget.Button, clickable=true, id=com.android.settings:id/button2
        //------------12: text='Borrar caché', class=android.widget.TextView, clickable=false, id=com.android.settings:id/text2

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=com.android.settings:id/action2 pkg=com.android.settings, identity=action2, bounds=Rect(540, 959 - 1020, 1239)
            ACS-DEBUG: --2: text='null', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button2 pkg=com.android.settings, identity=btn2, bounds=Rect(691, 959 - 869, 1098)
            ACS-DEBUG: --2: text='Borrar caché', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=com.android.settings:id/text2 pkg=com.android.settings, identity=text2, bounds=Rect(628, 1113 - 931, 1181)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        every { labels.getClearCacheStatic(any()) } returns setOf("Borrar caché", "Clear cache")

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // The clickable sibling Button should be clicked, not the TextView
        val clickableSibling = testRoot.crawl()
            .first { it.node.viewIdResourceName == "com.android.settings:id/button2" }.node as TestACSNodeInfo
        clickableSibling.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `clear cache finds node by content-desc when text is null - Android 16 API 36`() = runTest {
        // Android 16 Beta pattern: action2 has content-desc="Clear cache" but no text
        // The node is clickable and should be found by content-desc fallback
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(36) } returns true

        val acsLog = """
            ACS-DEBUG: 0: text='null', contentDesc='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', contentDesc='Clear storage', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/action1 pkg=com.android.settings, identity=action1, bounds=Rect(60, 861 - 540, 1107)
            ACS-DEBUG: -1: text='null', contentDesc='Clear cache', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/action2 pkg=com.android.settings, identity=action2, bounds=Rect(540, 861 - 1020, 1107)
        """.trimIndent()

        testRoot = buildTestTree(acsLog)

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // The action2 LinearLayout with content-desc="Clear cache" should be clicked
        val action2 = testRoot.crawl()
            .first { it.node.viewIdResourceName == "com.android.settings:id/action2" }.node as TestACSNodeInfo
        action2.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `clear cache quick-try succeeds via DPAD without Shizuku`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) {
                emitValidationEventAsync()
            }
            true
        }

        // Anchor present → quick-try bootstraps, fires RIGHT + CENTER, validation passes
        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=com.android.settings:id/content_parent pkg=com.android.settings, identity=content, bounds=Rect(0, 159 - 1080, 2300)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // Quick-try succeeds: 1 RIGHT + 1 CENTER
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `clear cache quick-try succeeds via DPAD on Motorola`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "motorola"
        every { BuildWrap.PRODUCT } returns "leap_g"

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) {
                emitValidationEventAsync()
            }
            true
        }

        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=com.android.settings:id/content_parent pkg=com.android.settings, identity=content, bounds=Rect(0, 159 - 1080, 2300)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `an already empty cache is confirmed before any blind DPAD click`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "motorola"
        every { BuildWrap.PRODUCT } returns "leap_g"

        // SizeParser goes through android.text.format.Formatter, an android.jar stub that throws
        // in this harness, which would make the size unreadable and the verdict inconclusive.
        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("0 B") } returns 0L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        // Anchor is there, so DPAD could run, but Settings prints an empty cache row and there is
        // no clickable clear-cache node at all.
        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=row, bounds=Rect(0, 900 - 1080, 1000)
            ACS-DEBUG: --2: text='Cache', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=rowTitle, bounds=Rect(60, 900 - 500, 1000)
            ACS-DEBUG: --2: text='0 B', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/summary pkg=com.android.settings, identity=rowSummary, bounds=Rect(600, 900 - 1020, 1000)
            """.trimIndent()
        )

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(maxAttempts = 15, rethrowAbort = true)
        }

        abort.treatAsSuccess shouldBe true
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `clear cache uses InputInjector DPAD path when injection is available`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns true
        coEvery { inputInjector.inject(any<InputInjector.Event>()) } coAnswers {
            if (firstArg<InputInjector.Event>() == InputInjector.Event.DpadCenter) {
                emitValidationEventAsync()
            }
            Unit
        }

        // Anchor exists -> fast-path fires: bootstrap + RIGHT + CENTER -> validation passes -> done
        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // Fast-path: 1 RIGHT + 1 CENTER, no cycle loop needed
        coVerify(exactly = 1) { inputInjector.inject(InputInjector.Event.DpadRight) }
        coVerify(exactly = 1) { inputInjector.inject(InputInjector.Event.DpadCenter) }
        verify(exactly = 0) { testHost.service.performGlobalAction(any()) }
    }

    @Test
    fun `clear cache does not run blind center sequence when anchor is missing`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        // No entity_header_content anchor in tree, so guarded blind fallback must not execute.
        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=com.android.settings:id/content_parent pkg=com.android.settings, identity=content, bounds=Rect(0, 159 - 1080, 2300)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe false
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `clear cache DPAD validation fails without post-click event`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe false
        // 1 quick-try + 3 blind-sweep (positions 2-4) = 4 CENTER, all fail without events
        verify(exactly = 4) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `clear cache DPAD validation fails when root package changes after click`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) {
                testHost.setWindowRoot(
                    buildTestTree(
                        """
                        ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.example.other, identity=root, bounds=Rect(0, 0 - 1080, 2400)
                        """.trimIndent()
                    )
                )
                emitValidationEventAsync()
            }
            true
        }

        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe false
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `InputInjector fast-path falls through to cycles on validation failure`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns true
        var centerCount = 0
        coEvery { inputInjector.inject(any<InputInjector.Event>()) } coAnswers {
            if (firstArg<InputInjector.Event>() == InputInjector.Event.DpadCenter) {
                centerCount++
                // First CENTER (fast-path) — no event emitted, validation times out
                // Second CENTER (blind fallback) — emit event, validation passes
                if (centerCount >= 2) {
                    emitValidationEventAsync()
                }
            }
            Unit
        }

        // Anchor stays present throughout so fall-through is allowed
        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // 1 quick-try + 4 cycles x 2 steps + 2 blind-sweep-2 = 11 RIGHT
        coVerify(exactly = 11) { inputInjector.inject(InputInjector.Event.DpadRight) }
        // 1 quick-try + 1 blind-sweep-2 = 2 CENTER
        coVerify(exactly = 2) { inputInjector.inject(InputInjector.Event.DpadCenter) }
    }

    @Test
    fun `InputInjector fast-path aborts when anchor disappears after failed validation`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns true
        coEvery { inputInjector.inject(any<InputInjector.Event>()) } coAnswers {
            if (firstArg<InputInjector.Event>() == InputInjector.Event.DpadCenter) {
                // Remove anchor from tree (simulates UI change), don't emit event
                testHost.setWindowRoot(
                    buildTestTree(
                        """
                        ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
                        """.trimIndent()
                    )
                )
            }
            Unit
        }

        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe false
        // Fast-path only: 1 RIGHT + 1 CENTER, then anchor gone → abort
        coVerify(exactly = 1) { inputInjector.inject(InputInjector.Event.DpadRight) }
        coVerify(exactly = 1) { inputInjector.inject(InputInjector.Event.DpadCenter) }
    }

    @Test
    fun `InputInjector fast-path skipped when anchor missing`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns true
        coEvery { inputInjector.inject(any<InputInjector.Event>()) } returns Unit

        // No anchor in tree → bootstrap fails → fast-path skipped → cycle loop also fails
        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=com.android.settings:id/content_parent pkg=com.android.settings, identity=content, bounds=Rect(0, 159 - 1080, 2300)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe false
        // No CENTER at all — bootstrap never succeeds
        coVerify(exactly = 0) { inputInjector.inject(InputInjector.Event.DpadCenter) }
    }

    @Test
    fun `DPAD bootstrap succeeds when anchor is already focused but performAction returns false`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) {
                emitValidationEventAsync()
            }
            true
        }

        // Anchor is already focused (isFocused=true) but performAction returns false
        // This simulates the Pixel 9 / Android 15 behavior
        val anchor = TestACSNodeInfo(
            className = "android.widget.LinearLayout",
            packageName = "com.android.settings",
            viewIdResourceName = "com.android.settings:id/entity_header_content",
            isClickable = true,
            isFocused = true,
            performActionResult = false,
        )
        testRoot = TestACSNodeInfo(
            className = "android.widget.FrameLayout",
            packageName = "com.android.settings",
        ).addChild(anchor) as TestACSNodeInfo

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // Bootstrap succeeds via isFocused → cycle loop runs → blind fallback fires
        verify(atLeast = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `blind sweep succeeds at position 3 when earlier positions fail`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"

        coEvery { inputInjector.canInject() } returns false
        var centerCount = 0
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) {
                centerCount++
                // Only 3rd CENTER (blind-sweep-3) emits event; quick-try and sweep-2 fail
                if (centerCount >= 3) {
                    emitValidationEventAsync()
                }
            }
            true
        }

        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // 1 quick-try + 8 cycles + 2 (sweep-2) + 3 (sweep-3) = 14 RIGHT
        verify(exactly = 14) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        // 1 quick-try + 1 sweep-2 + 1 sweep-3 = 3 CENTER
        verify(exactly = 3) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    // ============================================================
    // Event-based window transition tests
    // ============================================================

    @Test
    fun `clear cache works with event-based window transition`() = runTest {
        // Test that transitionTo() properly sets up window root and emits events
        // This validates the event infrastructure in TestAutomationHost
        setupTestScope(this)

        val acsLog = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=layout1, bounds=Rect(0, 0 - 1080, 400)
            ACS-DEBUG: --2: text='Clear cache', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=com.android.settings:id/button2 pkg=com.android.settings, identity=btn2, bounds=Rect(550, 100 - 1000, 150)
        """.trimIndent()

        val tree = buildTestTree(acsLog)

        // Use transitionTo() to set window root AND emit event
        testHost.transitionTo(tree, "com.android.settings")

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        val clearCacheButton = tree.crawl().first { it.node.text == "Clear cache" }.node as TestACSNodeInfo
        clearCacheButton.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `a non-allowlisted manufacturer never gets a DPAD click`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        // MANUFACTOR stays at the "TestOEM" default from aospSetup, so on 37 only the button band
        // check could open the fallback, and it can't qualify here: the tree is parsed without
        // bounds and the display density is unknown. The cache row is deliberately non-empty: if
        // the gate ever regressed, a zero row would set pendingVerdict at the first size check and
        // suppress the DPAD branch for the rest of the run, so the assertions below would still
        // pass and the regression would slip through.
        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        // Anchor is there, so DPAD could run, but there is no clickable clear-cache node at all.
        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=row, bounds=Rect(0, 900 - 1080, 1000)
            ACS-DEBUG: --2: text='Cache', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=rowTitle, bounds=Rect(60, 900 - 500, 1000)
            ACS-DEBUG: --2: text='143 kB', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/summary pkg=com.android.settings, identity=rowSummary, bounds=Rect(600, 900 - 1020, 1000)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction(maxAttempts = 5)

        result shouldBe false
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
    }

    @Test
    fun `an allowlisted manufacturer below API 36 never gets a DPAD click`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 35 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Sony"
        every { BuildWrap.PRODUCT } returns "XQ-GE54"

        // hasApiLevel stays below 36 while MANUFACTOR is allowlisted: the manufacturer alone must
        // not open the fallback, or every Sony device on 35 and older would start blind-clicking.
        // The cache row is deliberately non-empty: if the gate ever regressed, a zero row would
        // set pendingVerdict at the first size check and suppress the DPAD branch for the rest of
        // the run, so the assertions below would still pass and the regression would slip through.
        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        testRoot = buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=row, bounds=Rect(0, 900 - 1080, 1000)
            ACS-DEBUG: --2: text='Cache', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=rowTitle, bounds=Rect(60, 900 - 500, 1000)
            ACS-DEBUG: --2: text='143 kB', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/summary pkg=com.android.settings, identity=rowSummary, bounds=Rect(600, 900 - 1020, 1000)
            """.trimIndent()
        )

        val result = captureAndRunClearCacheAction(maxAttempts = 5)

        result shouldBe false
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
    }

    // ============================================================
    // Empty button band gate (non-allowlisted manufacturers, API 37+)
    // ============================================================

    @Test
    fun `an empty button band opens the DPAD fallback on API 37`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        stubDensity()

        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L
        every { anyConstructed<SizeParser>().parse("12 kB") } returns 12288L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) {
                testHost.setWindowRoot(buttonBandTree(cacheSummary = "12 kB"))
                emitValidationEventAsync()
            }
            true
        }

        testRoot = buttonBandTree()

        // Band observations land on attempts 2 and 12, so DPAD can only start on attempt 12.
        val result = captureAndRunClearCacheAction(maxAttempts = 13)

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a band-gated DPAD click without a cache decrease does not succeed`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        stubDensity()

        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) {
                emitValidationEventAsync()
            }
            true
        }

        testRoot = buttonBandTree()

        val result = captureAndRunClearCacheAction(maxAttempts = 13)

        result shouldBe false
        // 1 quick-try + 3 blind-sweep (positions 2-4), each rejected by the delta check
        verify(exactly = 4) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a node inside the button band keeps the DPAD fallback closed`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        stubDensity()

        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        testRoot = buttonBandTree(stripRow = true)

        val result = captureAndRunClearCacheAction(maxAttempts = 13)

        result shouldBe false
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
    }

    @Test
    fun `an empty button band on API 36 keeps the DPAD fallback closed`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        stubDensity()

        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        testRoot = buttonBandTree()

        val result = captureAndRunClearCacheAction(maxAttempts = 13)

        result shouldBe false
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
    }

    @Test
    fun `an allowlisted manufacturer gets DPAD regardless of the button band`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"
        stubDensity()

        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L
        every { anyConstructed<SizeParser>().parse("12 kB") } returns 12288L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) {
                testHost.setWindowRoot(buttonBandTree(cacheSummary = "12 kB", stripRow = true))
                emitValidationEventAsync()
            }
            true
        }

        // The strip is occupied, which would veto the band route, but the allowlist never asks.
        testRoot = buttonBandTree(stripRow = true)

        val result = captureAndRunClearCacheAction(maxAttempts = 13)

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `an empty button band with unknown display density keeps the DPAD fallback closed`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        // No stubDensity(): the relaxed service mock reports a density of 0.

        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        testRoot = buttonBandTree()

        val result = captureAndRunClearCacheAction(maxAttempts = 13)

        result shouldBe false
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
    }

    @Test
    fun `a row appearing in the button band during layout stabilization vetoes the first key`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        stubDensity()

        // The second size read (attempt 12) follows the confirming band observation and directly
        // precedes the DPAD branch, whose layout stabilization waits 200ms between its checks.
        // The row shows up halfway through that wait.
        mockkConstructor(SizeParser::class)
        var sizeReads = 0
        every { anyConstructed<SizeParser>().parse("143 kB") } answers {
            sizeReads++
            if (sizeReads == 2) {
                testHost.scope.launch {
                    delay(100)
                    testHost.setWindowRoot(buttonBandTree(stripRow = true))
                }
            }
            143360L
        }

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        testRoot = buttonBandTree()

        // rethrowAbort: a veto must not count as exhausted, which would abort on attempt 12.
        val result = captureAndRunClearCacheAction(maxAttempts = 23, rethrowAbort = true)

        result shouldBe false
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
    }

    @Test
    fun `a single empty band observation does not open the DPAD fallback`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        stubDensity()

        // Observations on attempts 2, 12 and 22 see empty, occupied, empty. Each size read follows
        // the observation of its pass, so it swaps in the tree for the next one. Only a streak
        // that resets on the occupied observation keeps attempt 22 from confirming.
        mockkConstructor(SizeParser::class)
        var sizeReads = 0
        every { anyConstructed<SizeParser>().parse("143 kB") } answers {
            sizeReads++
            when (sizeReads) {
                1 -> testHost.setWindowRoot(buttonBandTree(stripRow = true))
                2 -> testHost.setWindowRoot(buttonBandTree())
            }
            143360L
        }

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } returns true

        testRoot = buttonBandTree()

        val result = captureAndRunClearCacheAction(maxAttempts = 23)

        result shouldBe false
        sizeReads shouldBe 3
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 0) { testHost.service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
    }

    // ============================================================
    // DPAD focus guard
    // ============================================================

    // Storage page with only the app header; [extraNodes] are appended to the root, e.g. app bar
    // buttons or the node that holds input focus.
    private fun focusGuardPage(vararg extraNodes: TestACSNodeInfo): TestACSNodeInfo = buildTestTree(
        "ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)\n" +
            "ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(42, 300 - 1038, 702)",
        withBounds = true,
    ).addChildren(*extraNodes)

    private fun settingsNode(
        className: String,
        contentDesc: String?,
        bounds: ACSNodeInfo.ScreenBounds,
        focused: Boolean,
    ) = TestACSNodeInfo(
        className = className,
        contentDescription = contentDesc,
        packageName = "com.android.settings",
        isClickable = true,
        isFocused = focused,
        screenBoundsOverride = bounds,
    )

    // Above the header of both focusGuardPage (top 300) and buttonBandTree (top 260).
    private fun navigateUpButton(focused: Boolean = false) = settingsNode(
        className = "android.widget.ImageButton",
        contentDesc = "Navigate up",
        bounds = ACSNodeInfo.ScreenBounds(0, 90, 132, 222),
        focused = focused,
    )

    private fun searchButton(focused: Boolean = false) = settingsNode(
        className = "android.widget.Button",
        contentDesc = "Search settings",
        bounds = ACSNodeInfo.ScreenBounds(948, 90, 1080, 222),
        focused = focused,
    )

    @Test
    fun `focus on the app bar search button after RIGHT never gets a DPAD_CENTER`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "motorola"
        every { BuildWrap.PRODUCT } returns "leap_g"

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) {
                testHost.setWindowRoot(focusGuardPage(navigateUpButton(), searchButton(focused = true)))
            }
            true
        }

        testRoot = focusGuardPage(navigateUpButton(), searchButton())

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        // 1 quick-try + 4 cycles + 3 blind-sweep positions
        verify(exactly = 8) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        // 1 quick-try + 4 cycles x 2 steps + 2 + 3 + 4 blind-sweep
        verify(exactly = 18) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 0) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `focus on the app bar up button after DOWN never gets a DPAD_CENTER`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "motorola"
        every { BuildWrap.PRODUCT } returns "leap_g"

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) {
                testHost.setWindowRoot(focusGuardPage(navigateUpButton(focused = true), searchButton()))
            }
            true
        }

        testRoot = focusGuardPage(navigateUpButton(), searchButton())

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 8) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 18) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 0) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `focus on the app bar search button blocks DPAD_CENTER on the button band route`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        stubDensity()

        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) {
                testHost.setWindowRoot(buttonBandTree().addChildren(navigateUpButton(), searchButton(focused = true)))
            }
            true
        }

        testRoot = buttonBandTree().addChildren(navigateUpButton(), searchButton())

        // The band is confirmed on attempt 12, which runs DPAD and ends in the exhaustion abort.
        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(maxAttempts = 13, rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 8) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 18) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 0) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `focus on the app bar search button never gets an injected DPAD_CENTER`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "motorola"
        every { BuildWrap.PRODUCT } returns "leap_g"

        coEvery { inputInjector.canInject() } returns true
        coEvery { inputInjector.inject(any<InputInjector.Event>()) } coAnswers {
            if (firstArg<InputInjector.Event>() == InputInjector.Event.DpadRight) {
                testHost.setWindowRoot(focusGuardPage(navigateUpButton(), searchButton(focused = true)))
            }
            Unit
        }

        testRoot = focusGuardPage(navigateUpButton(), searchButton())

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        coVerify(exactly = 8) { inputInjector.inject(InputInjector.Event.DpadDown) }
        coVerify(exactly = 18) { inputInjector.inject(InputInjector.Event.DpadRight) }
        coVerify(exactly = 0) { inputInjector.inject(InputInjector.Event.DpadCenter) }
        verify(exactly = 0) { testHost.service.performGlobalAction(any()) }
    }

    @Test
    fun `anchor disappearing before DPAD_CENTER vetoes the press`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "motorola"
        every { BuildWrap.PRODUCT } returns "leap_g"

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) {
                testHost.setWindowRoot(
                    buildTestTree(
                        "ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)",
                        withBounds = true,
                    )
                )
            }
            true
        }

        testRoot = focusGuardPage(navigateUpButton(), searchButton())

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        // Quick-try only: the anchor stays gone, so neither cycles nor blind sweep get a key in.
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 0) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `focus below the header keeps the DPAD_CENTER`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        mockkObject(BuildWrap)
        every { BuildWrap.MANUFACTOR } returns "motorola"
        every { BuildWrap.PRODUCT } returns "leap_g"

        // Unlabeled node in the strip below the header, where the withheld button row is drawn.
        val rowNode = TestACSNodeInfo(
            className = "android.widget.LinearLayout",
            packageName = "com.android.settings",
            isFocused = true,
            screenBoundsOverride = ACSNodeInfo.ScreenBounds(42, 760, 520, 880),
        )

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            when (firstArg<Int>()) {
                AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT -> {
                    testHost.setWindowRoot(focusGuardPage(navigateUpButton(), searchButton(), rowNode))
                }

                AccessibilityService.GLOBAL_ACTION_DPAD_CENTER -> emitValidationEventAsync()
            }
            true
        }

        testRoot = focusGuardPage(navigateUpButton(), searchButton())

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    // ============================================================
    // Containing a DPAD press that left the storage page
    // ============================================================

    private fun storagePage() = buildTestTree(
        """
        ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
        ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)
        """.trimIndent()
    )

    // What a press on "Clear storage" opens.
    private fun deleteAppDataDialog() = buildTestTree(
        """
        ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
        ACS-DEBUG: -1: text='Delete app data?', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/alertTitle pkg=com.android.settings, identity=title, bounds=Rect(100, 900 - 980, 980)
        ACS-DEBUG: -1: text='Cancel', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=android:id/button2 pkg=com.android.settings, identity=cancel, bounds=Rect(500, 1300 - 720, 1400)
        ACS-DEBUG: -1: text='Delete', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=android:id/button1 pkg=com.android.settings, identity=delete, bounds=Rect(740, 1300 - 980, 1400)
        """.trimIndent()
    )

    // The target app's own manage-space activity; createTestPkg() is "test.pkg".
    private fun targetAppScreen() = buildTestTree(
        "ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=test.pkg, identity=root, bounds=Rect(0, 0 - 1080, 2400)"
    )

    private fun setupAllowlistedDpad(scope: TestScope) {
        setupTestScope(scope)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 36 }
        every { BuildWrap.MANUFACTOR } returns "Google"
        every { BuildWrap.PRODUCT } returns "lynx_beta"
        coEvery { inputInjector.canInject() } returns false
    }

    private fun stubManageStorageText(present: Boolean) {
        val settingsResources = mockk<Resources> {
            every { getIdentifier("manage_storage_text", "string", "com.android.settings") } returns if (present) 1 else 0
        }
        every { testHost.service.packageManager.getResourcesForApplication("com.android.settings") } returns settingsResources
    }

    // Records every global action in order. BACK runs [onBack], DPAD_CENTER runs [onCenter] with
    // its 1-based count.
    private fun recordGlobalActions(
        onBack: () -> Unit = { testHost.setWindowRoot(storagePage()) },
        onCenter: (Int) -> Unit,
    ): List<Int> {
        val actions = mutableListOf<Int>()
        var centers = 0
        every { testHost.service.performGlobalAction(any()) } answers {
            val action = firstArg<Int>()
            actions += action
            when (action) {
                AccessibilityService.GLOBAL_ACTION_DPAD_CENTER -> onCenter(++centers)
                AccessibilityService.GLOBAL_ACTION_BACK -> onBack()
            }
            true
        }
        return actions
    }

    private fun List<Int>.afterLastCenter() = drop(lastIndexOf(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) + 1)

    @Test
    fun `a blind sweep press that opens a dialog gets one BACK and ends the attempt`() = runTest {
        setupAllowlistedDpad(this)
        val actions = recordGlobalActions { center ->
            // The quick-try press does nothing, the blind-sweep-2 press opens the dialog.
            if (center == 2) {
                testHost.setWindowRoot(deleteAppDataDialog())
                emitValidationEventAsync()
            }
        }
        testRoot = storagePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        abort.replayable shouldBe false
        abort.isAutomationUnusable() shouldBe true
        verify(exactly = 2) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        actions.afterLastCenter() shouldBe listOf(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test
    fun `a quick-try press that opens a dialog gets one BACK and ends the attempt`() = runTest {
        setupAllowlistedDpad(this)
        val actions = recordGlobalActions {
            testHost.setWindowRoot(deleteAppDataDialog())
            emitValidationEventAsync()
        }
        testRoot = storagePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        actions.afterLastCenter() shouldBe listOf(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test
    fun `a window root that is missing after the press still gets the BACK once the dialog shows`() = runTest {
        setupAllowlistedDpad(this)
        val actions = recordGlobalActions {
            testHost.setWindowRoot(null)
            // Validation (800ms) and the detour check (1s) both see no root, the dialog shows up
            // while containment is polling for one.
            testHost.scope.launch {
                delay(2_200)
                testHost.setWindowRoot(deleteAppDataDialog())
            }
        }
        testRoot = storagePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        actions.afterLastCenter() shouldBe listOf(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test
    fun `an event before the dialog attaches is no success without size rows`() = runTest {
        setupAllowlistedDpad(this)
        val actions = recordGlobalActions {
            // The press's own content change arrives while the storage page is still shown.
            emitValidationEventAsync()
            testHost.scope.launch {
                delay(100)
                testHost.setWindowRoot(deleteAppDataDialog())
            }
        }
        testRoot = storagePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        actions.afterLastCenter() shouldBe listOf(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test
    fun `an event before the dialog attaches is no success with size rows`() = runTest {
        setupAllowlistedDpad(this)
        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L
        val actions = recordGlobalActions(onBack = { testHost.setWindowRoot(buttonBandTree()) }) {
            emitValidationEventAsync()
            testHost.scope.launch {
                delay(100)
                testHost.setWindowRoot(deleteAppDataDialog())
            }
        }
        testRoot = buttonBandTree()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        actions.afterLastCenter() shouldBe listOf(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test
    fun `a cache decrease read without the anchor is no success`() = runTest {
        setupAllowlistedDpad(this)
        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L
        every { anyConstructed<SizeParser>().parse("12 kB") } returns 12288L
        recordGlobalActions(onBack = { testHost.setWindowRoot(buttonBandTree()) }) {
            testHost.setWindowRoot(buttonBandTree(cacheSummary = "12 kB", header = false))
            emitValidationEventAsync()
        }
        testRoot = buttonBandTree()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    }

    @Test
    fun `a detour into the app without manage_storage_text gets one BACK and no retry`() = runTest {
        setupAllowlistedDpad(this)
        stubManageStorageText(present = false)
        val actions = recordGlobalActions { testHost.setWindowRoot(targetAppScreen()) }
        testRoot = storagePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        actions.afterLastCenter() shouldBe listOf(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test
    fun `a detour into the app with manage_storage_text is retried`() = runTest {
        setupAllowlistedDpad(this)
        stubManageStorageText(present = true)
        recordGlobalActions { center ->
            when (center) {
                1 -> testHost.setWindowRoot(targetAppScreen())
                else -> emitValidationEventAsync()
            }
        }
        testRoot = storagePage()

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // Quick-try DOWN, RIGHT, CENTER; then BACK and the retry's DOWN, RIGHT, LEFT, CENTER.
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        verify(exactly = 2) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 2) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT) }
        verify(exactly = 2) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a DPAD run that hits its deadline counts as exhausted and is still contained`() = runTest {
        setupAllowlistedDpad(this)
        coEvery { inputInjector.canInject() } returns true
        var centerAt = -1L
        coEvery { inputInjector.inject(any<InputInjector.Event>()) } coAnswers {
            if (firstArg<InputInjector.Event>() == InputInjector.Event.DpadCenter) {
                centerAt = testScheduler.currentTime
                testHost.setWindowRoot(deleteAppDataDialog())
                // Only the deadline ends this press.
                delay(60_000)
            }
            Unit
        }
        var backAt = -1L
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == AccessibilityService.GLOBAL_ACTION_BACK) {
                backAt = testScheduler.currentTime
                testHost.setWindowRoot(storagePage())
            }
            true
        }
        testRoot = storagePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        coVerify(exactly = 1) { inputInjector.inject(InputInjector.Event.DpadCenter) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        (backAt - centerAt) shouldBeGreaterThan 0L
        (backAt - centerAt) shouldBeLessThan 10_000L
    }

    @Test
    fun `a DPAD run only gets what the step attempt has left and is still contained`() = runTest {
        setupAllowlistedDpad(this)
        coEvery { inputInjector.canInject() } returns true
        val keyTimes = mutableListOf<Long>()
        coEvery { inputInjector.inject(any<InputInjector.Event>()) } coAnswers {
            keyTimes += testScheduler.currentTime
            if (firstArg<InputInjector.Event>() == InputInjector.Event.DpadCenter) {
                testHost.setWindowRoot(deleteAppDataDialog())
                // Only the deadline ends this press.
                delay(60_000)
            }
            Unit
        }
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == AccessibilityService.GLOBAL_ACTION_BACK) testHost.setWindowRoot(storagePage())
            true
        }
        testRoot = storagePage()

        // 6s left of the attempt, minus the 3.5s reserve, leaves the run 2.5s.
        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(
                rethrowAbort = true,
                attemptDeadline = testScheduler.timeSource.markNow() + 6.seconds,
            )
        }

        abort.message shouldContain "DPAD exhausted"
        abort.replayable shouldBe false
        testScheduler.currentTime shouldBeLessThan 6_000L
        keyTimes.max() shouldBeLessThan 2_500L
        coVerify(exactly = 1) { inputInjector.inject(InputInjector.Event.DpadCenter) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    }

    @Test
    fun `a step attempt without time for a DPAD run sends no key`() = runTest {
        setupAllowlistedDpad(this)
        recordGlobalActions { emitValidationEventAsync() }
        testRoot = storagePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(
                rethrowAbort = true,
                attemptDeadline = testScheduler.timeSource.markNow() + 3.seconds,
            )
        }

        abort.message shouldContain "DPAD exhausted"
        abort.replayable shouldBe false
        verify(exactly = 0) { testHost.service.performGlobalAction(any()) }
    }

    @Test
    fun `the nodeAction call whose DPAD run is exhausted throws the abort itself`() = runTest {
        setupAllowlistedDpad(this)
        recordGlobalActions {
            testHost.setWindowRoot(deleteAppDataDialog())
            emitValidationEventAsync()
        }
        testRoot = storagePage()

        // The DPAD runs on attempt 2, the last nodeAction call here.
        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(maxAttempts = 3, rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        abort.message shouldContain "attempt=2"
        abort.replayable shouldBe false
    }

    // ============================================================
    // Verifying DPAD presses by the target's cache bytes
    // ============================================================

    private fun cacheOf(bytes: Long) = InaccessibleCache(
        identifier = InstallId("test.pkg".toPkgId(), UserHandle2(0)),
        isSystemApp = false,
        itemCount = 2,
        totalSize = bytes,
        publicSize = null,
        theoreticalPaths = emptySet(),
    )

    // Storage page as the reporter's Pixel 9 Pro (Android 17) exposed it: no Cache row in the accessibility tree, and "User data" excludes the cache.
    // With [cache], the Cache row a Pixel 7 and Pixel 10 Pro (Android 17) do show.
    // [header] = false drops entity_header_content.
    private fun pixelStoragePage(
        userData: String = "3.52 MB",
        cache: String? = null,
        header: Boolean = true,
    ): TestACSNodeInfo {
        val cacheRow = if (cache != null) {
            """

            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=cacheRow, bounds=Rect(0, 1200 - 1080, 1300)
            ACS-DEBUG: --2: text='Cache', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=cacheTitle, bounds=Rect(60, 1200 - 500, 1300)
            ACS-DEBUG: --2: text='$cache', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/summary pkg=com.android.settings, identity=cacheSummary, bounds=Rect(600, 1200 - 1020, 1300)
            """.trimIndent()
        } else {
            ""
        }
        val headerLine = if (header) {
            "ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=Rect(84, 328 - 996, 675)"
        } else {
            ""
        }
        return buildTestTree(
            """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=Rect(0, 0 - 1080, 2400)
            $headerLine
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=appSizeRow, bounds=Rect(0, 1000 - 1080, 1100)
            ACS-DEBUG: --2: text='App size', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=appSizeTitle, bounds=Rect(60, 1000 - 500, 1100)
            ACS-DEBUG: --2: text='90.60 MB', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/summary pkg=com.android.settings, identity=appSizeSummary, bounds=Rect(600, 1000 - 1020, 1100)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=userDataRow, bounds=Rect(0, 1100 - 1080, 1200)
            ACS-DEBUG: --2: text='User data', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=userDataTitle, bounds=Rect(60, 1100 - 500, 1200)
            ACS-DEBUG: --2: text='$userData', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/summary pkg=com.android.settings, identity=userDataSummary, bounds=Rect(600, 1100 - 1020, 1200)
            """.trimIndent() + cacheRow
        )
    }

    private fun stubPixelSizes() {
        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("90.60 MB") } returns 90_600_000L
        every { anyConstructed<SizeParser>().parse("3.52 MB") } returns 3_520_000L
        every { anyConstructed<SizeParser>().parse("42.93 MB") } returns 42_930_000L
        every { anyConstructed<SizeParser>().parse("0 B") } returns 0L
    }

    @Test
    fun `a quick-try press counts once the cache bytes drop although the storage page does not change`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        var centerAt = -1L
        coEvery { cacheProvider.determineCache(any()) } answers {
            // StorageStats only catch up 2s after the press.
            val cleared = centerAt >= 0 && testScheduler.currentTime >= centerAt + 2_000
            cacheOf(if (cleared) 0L else 50_000L)
        }
        recordGlobalActions {
            centerAt = testScheduler.currentTime
            emitValidationEventAsync()
        }
        testRoot = pixelStoragePage()

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    }

    @Test
    fun `a quick-try press counts on dropped cache bytes without any accessibility event`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        var centerSent = false
        coEvery { cacheProvider.determineCache(any()) } answers { cacheOf(if (centerSent) 12_000L else 50_000L) }
        recordGlobalActions { centerSent = true }
        testRoot = pixelStoragePage()

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a zero storage row is no success while the cache bytes stay at the baseline`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        coEvery { cacheProvider.determineCache(any()) } returns cacheOf(50_000L)
        recordGlobalActions { emitValidationEventAsync() }
        testRoot = pixelStoragePage(userData = "0 B")

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(atLeast = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a storage row turning unparseable is no success while the cache bytes stay at the baseline`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        coEvery { cacheProvider.determineCache(any()) } returns cacheOf(50_000L)
        recordGlobalActions {
            testHost.setWindowRoot(pixelStoragePage(userData = "Calculating…"))
            emitValidationEventAsync()
        }
        testRoot = pixelStoragePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(atLeast = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a zero cache baseline ends the target as already empty before any DPAD key`() = runTest {
        setupAllowlistedDpad(this)
        coEvery { cacheProvider.determineCache(any()) } returns cacheOf(0L)
        recordGlobalActions { emitValidationEventAsync() }
        testRoot = storagePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.treatAsSuccess shouldBe true
        abort.message shouldContain "already empty"
        verify(exactly = 0) { testHost.service.performGlobalAction(any()) }
    }

    private suspend fun assertStoragePageDecides() {
        mockkConstructor(SizeParser::class)
        every { anyConstructed<SizeParser>().parse("143 kB") } returns 143360L
        every { anyConstructed<SizeParser>().parse("12 kB") } returns 12288L
        recordGlobalActions {
            testHost.setWindowRoot(buttonBandTree(cacheSummary = "12 kB"))
            emitValidationEventAsync()
        }
        testRoot = buttonBandTree()

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        coVerify(exactly = 1) { cacheProvider.determineCache(any()) }
    }

    @Test
    fun `without cache stats the storage page delta still decides`() = runTest {
        setupAllowlistedDpad(this)
        coEvery { cacheProvider.determineCache(any()) } returns null
        assertStoragePageDecides()
    }

    @Test
    fun `a cache baseline read that times out leaves the storage page delta to decide`() = runTest {
        setupAllowlistedDpad(this)
        coEvery { cacheProvider.determineCache(any()) } coAnswers {
            delay(60_000)
            cacheOf(50_000L)
        }
        assertStoragePageDecides()
    }

    @Test
    fun `a cache drop that only shows after the run's deadline is caught by the final re-check`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        // The run starts at virtual time 0, so only reads after its 10s deadline see the drop: the
        // quick-try poll and the blind sweep that follows still read the old size.
        coEvery { cacheProvider.determineCache(any()) } answers {
            cacheOf(if (testScheduler.currentTime >= 10_000) 0L else 50_000L)
        }
        recordGlobalActions { emitValidationEventAsync() }
        testRoot = pixelStoragePage()

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        // Quick-try and at least blind-sweep-2.
        verify(atLeast = 2) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    }

    @Test
    fun `the final cache re-check runs after the open dialog was contained`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        var backSent = false
        coEvery { cacheProvider.determineCache(any()) } answers { cacheOf(if (backSent) 0L else 50_000L) }
        val actions = recordGlobalActions(
            onBack = {
                backSent = true
                testHost.setWindowRoot(pixelStoragePage())
            },
        ) {
            testHost.setWindowRoot(deleteAppDataDialog())
        }
        testRoot = pixelStoragePage()

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        actions.afterLastCenter() shouldBe listOf(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test
    fun `a cache baseline read that never returns is abandoned and never started twice`() = runTest {
        setupTestScope(this)
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 37 }
        stubDensity()

        val release = CountDownLatch(1)
        var reads = 0
        coEvery { cacheProvider.determineCache(any()) } coAnswers {
            reads++
            // A Binder call that never returns, run like IPCFunnel runs it: blocking an IO thread.
            withContext(Dispatchers.IO) { release.await() }
            cacheOf(50_000L)
        }

        // The band is confirmed on attempt 12. While that run waits out the hung baseline read, a row
        // appears in the band, so the run stops before its first key. The row is gone again by the
        // size read on attempt 22, and the band is confirmed once more on attempt 42 for a second run.
        mockkConstructor(SizeParser::class)
        var sizeReads = 0
        every { anyConstructed<SizeParser>().parse("143 kB") } answers {
            sizeReads++
            when (sizeReads) {
                2 -> testHost.scope.launch {
                    delay(100)
                    testHost.setWindowRoot(buttonBandTree(stripRow = true))
                }

                3 -> testHost.setWindowRoot(buttonBandTree())
            }
            143360L
        }
        every { anyConstructed<SizeParser>().parse("12 kB") } returns 12288L

        coEvery { inputInjector.canInject() } returns false
        every { testHost.service.performGlobalAction(any()) } answers {
            if (firstArg<Int>() == AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) {
                testHost.setWindowRoot(buttonBandTree(cacheSummary = "12 kB"))
                emitValidationEventAsync()
            }
            true
        }
        testRoot = buttonBandTree()

        try {
            val result = captureAndRunClearCacheAction(maxAttempts = 43, rethrowAbort = true)

            result shouldBe true
            reads shouldBe 1
            verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
            verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        } finally {
            release.countDown()
        }
    }

    // ============================================================
    // Starting the DPAD only on a window at rest
    // ============================================================

    private fun stubDisplayWidth(width: Int) {
        val displayRect = Rect().apply {
            left = 0
            top = 0
            right = width
            bottom = 2142
        }
        val metrics = mockk<WindowMetrics> { every { bounds } returns displayRect }
        val windowManager = mockk<WindowManager> { every { maximumWindowMetrics } returns metrics }
        every { testHost.service.getSystemService(WindowManager::class.java) } returns windowManager
    }

    // Storage page on a 960px wide display. While Settings' entry transition runs, the root still sits
    // to the right and the anchor keeps reporting bounds it only has once the window is at rest.
    private fun transitionPage(sliding: Boolean): TestACSNodeInfo {
        val rootBounds = if (sliding) "Rect(217, 0 - 1177, 2142)" else "Rect(0, 0 - 960, 2142)"
        val headerBounds = if (sliding) "Rect(305, 373 - 1089, 819)" else "Rect(88, 373 - 872, 819)"
        return buildTestTree(
            "ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=root, bounds=$rootBounds\n" +
                "ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=true, checkable=false enabled=true, id=com.android.settings:id/entity_header_content pkg=com.android.settings, identity=header, bounds=$headerBounds",
            withBounds = true,
        )
    }

    // Returns the virtual time of every global action. DPAD_CENTER emits the validation event.
    private fun TestScope.recordGlobalActionTimes(): List<Long> {
        val times = mutableListOf<Long>()
        every { testHost.service.performGlobalAction(any()) } answers {
            times += testScheduler.currentTime
            if (firstArg<Int>() == AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) emitValidationEventAsync()
            true
        }
        return times
    }

    @Test
    fun `no DPAD key is sent before the storage page has come to rest`() = runTest {
        setupAllowlistedDpad(this)
        stubDisplayWidth(960)
        val keyTimes = recordGlobalActionTimes()
        // The run's cache baseline read marks its start; the transition ends 1s later.
        var runStartAt = -1L
        coEvery { cacheProvider.determineCache(any()) } answers {
            runStartAt = testScheduler.currentTime
            testHost.scope.launch {
                delay(1_000)
                testHost.setWindowRoot(transitionPage(sliding = false))
            }
            null
        }
        testRoot = transitionPage(sliding = true)

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        (runStartAt >= 0) shouldBe true
        keyTimes.min() shouldBeGreaterThanOrEqual runStartAt + 1_000
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a storage page that never comes to rest ends the attempt without any DPAD key`() = runTest {
        setupAllowlistedDpad(this)
        stubDisplayWidth(960)
        recordGlobalActionTimes()
        testRoot = transitionPage(sliding = true)

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        abort.replayable shouldBe false
        verify(exactly = 0) { testHost.service.performGlobalAction(any()) }
    }

    @Test
    fun `a zero cache baseline ends the target as already empty on a page that never comes to rest`() = runTest {
        setupAllowlistedDpad(this)
        stubDisplayWidth(960)
        coEvery { cacheProvider.determineCache(any()) } returns cacheOf(0L)
        recordGlobalActionTimes()
        testRoot = transitionPage(sliding = true)

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.treatAsSuccess shouldBe true
        abort.message shouldContain "already empty"
        verify(exactly = 0) { testHost.service.performGlobalAction(any()) }
    }

    @Test
    fun `an unknown display width lets the DPAD start on a page that is still sliding in`() = runTest {
        setupAllowlistedDpad(this)
        stubDisplayWidth(0)
        val keyTimes = recordGlobalActionTimes()
        var runStartAt = -1L
        coEvery { cacheProvider.determineCache(any()) } answers {
            runStartAt = testScheduler.currentTime
            null
        }
        testRoot = transitionPage(sliding = true)

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        (runStartAt >= 0) shouldBe true
        (keyTimes.min() - runStartAt) shouldBeLessThan 1_000L
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a cache read that answers after its poll timeout is still used by the next poll`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        var centerSent = false
        // Every read after the press outlasts its 250ms poll timeout.
        coEvery { cacheProvider.determineCache(any()) } coAnswers {
            if (centerSent) {
                delay(400)
                cacheOf(0L)
            } else {
                cacheOf(50_000L)
            }
        }
        recordGlobalActions {
            centerSent = true
            emitValidationEventAsync()
        }
        testRoot = pixelStoragePage()

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a final cache drop with the dialog still open is no success`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        var backSent = false
        coEvery { cacheProvider.determineCache(any()) } answers { cacheOf(if (backSent) 0L else 50_000L) }
        recordGlobalActions(onBack = { backSent = true }) {
            testHost.setWindowRoot(deleteAppDataDialog())
        }
        testRoot = pixelStoragePage()

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a quick-try press counts once the Cache row drops although the cache bytes stay at the baseline`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        coEvery { cacheProvider.determineCache(any()) } returns cacheOf(42_930_176L)
        recordGlobalActions {
            testHost.setWindowRoot(pixelStoragePage(cache = "0 B"))
            emitValidationEventAsync()
        }
        testRoot = pixelStoragePage(cache = "42.93 MB")

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    }

    @Test
    fun `another row dropping is no success while the Cache row and the cache bytes stay`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        coEvery { cacheProvider.determineCache(any()) } returns cacheOf(42_930_176L)
        recordGlobalActions {
            testHost.setWindowRoot(pixelStoragePage(userData = "0 B", cache = "42.93 MB"))
            emitValidationEventAsync()
        }
        testRoot = pixelStoragePage(cache = "42.93 MB")

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(atLeast = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a Cache row turning unparseable is no success while the cache bytes stay at the baseline`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        coEvery { cacheProvider.determineCache(any()) } returns cacheOf(42_930_176L)
        recordGlobalActions {
            testHost.setWindowRoot(pixelStoragePage(cache = "Calculating…"))
            emitValidationEventAsync()
        }
        testRoot = pixelStoragePage(cache = "42.93 MB")

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(atLeast = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
    }

    @Test
    fun `a Cache row drop that only shows after the run's deadline is caught by the final re-check`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        coEvery { cacheProvider.determineCache(any()) } returns cacheOf(42_930_176L)
        // The run starts at virtual time 0, so only reads after its 10s deadline see the cleared row.
        every { anyConstructed<SizeParser>().parse("42.93 MB") } answers {
            if (testScheduler.currentTime >= 10_000) 0L else 42_930_000L
        }
        recordGlobalActions { emitValidationEventAsync() }
        testRoot = pixelStoragePage(cache = "42.93 MB")

        val result = captureAndRunClearCacheAction()

        result shouldBe true
        verify(atLeast = 2) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 0) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    }

    @Test
    fun `a Cache row drop without the storage page anchor is no success`() = runTest {
        setupAllowlistedDpad(this)
        stubPixelSizes()
        coEvery { cacheProvider.determineCache(any()) } returns cacheOf(42_930_176L)
        recordGlobalActions(onBack = {}) {
            testHost.setWindowRoot(pixelStoragePage(cache = "0 B", header = false))
        }
        testRoot = pixelStoragePage(cache = "42.93 MB")

        val abort = shouldThrow<StepAbortException> {
            captureAndRunClearCacheAction(rethrowAbort = true)
        }

        abort.message shouldContain "DPAD exhausted"
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER) }
        verify(exactly = 1) { testHost.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
    }
}
