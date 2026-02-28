package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.appcleaner.core.automation.specs.BaseAppCleanerSpecTest
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.input.InputInjector
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.hasApiLevel
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.TestACSNodeInfo

class AOSPSpecsTest : BaseAppCleanerSpecTest<AOSPSpecs, AOSPLabels>() {

    override val romType = RomType.AOSP

    private val inputInjector: InputInjector = mockk<InputInjector>().apply {
        coEvery { canInject() } returns false
    }

    override fun createLabels(): AOSPLabels = mockk()

    override fun createSpec() = AOSPSpecs(
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        aospLabels = labels,
        storageEntryFinder = storageEntryFinder,
        generalSettings = generalSettings,
        stepper = stepper,
        inputInjector = inputInjector,
    )

    override fun mockLabelDefaults() {
        every { labels.getStorageEntryDynamic(any()) } returns emptySet()
        every { labels.getStorageEntryStatic(any()) } returns setOf("Storage")
        every { labels.getClearCacheDynamic(any()) } returns emptySet()
        every { labels.getClearCacheStatic(any()) } returns setOf("Clear cache")
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
    }

    private fun emitValidationEventAsync(pkgId: String = "com.android.settings") {
        testHost.scope.launch {
            delay(1)
            testHost.emitEvent(pkgId, android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
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
}
