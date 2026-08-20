package eu.darken.sdmse.main.core.shortcuts

import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.mockDataStoreValue

/**
 * The clean-shortcut trampoline is exported: the tool extra is attacker-controllable and the opt-in
 * can have been switched off after a shortcut was pinned. Both re-checks live in [CleanShortcutRouter]
 * so they can be pinned here without Hilt.
 */
class CleanShortcutRouterTest : BaseTest() {

    @MockK lateinit var generalSettings: GeneralSettings
    @MockK lateinit var oneTapCleaner: OneTapCleaner

    @BeforeEach
    fun init() {
        MockKAnnotations.init(this)
    }

    private fun setup(
        enabledTools: Set<SDMTool.Type> = OneTapCleaner.ONECLICK_TYPES,
        outcome: OneTapCleaner.Outcome = OneTapCleaner.Outcome.Ran,
    ) {
        every {
            generalSettings.shortcutCleanCorpseFinderEnabled
        } returns mockDataStoreValue(enabledTools.contains(SDMTool.Type.CORPSEFINDER))
        every {
            generalSettings.shortcutCleanSystemCleanerEnabled
        } returns mockDataStoreValue(enabledTools.contains(SDMTool.Type.SYSTEMCLEANER))
        every {
            generalSettings.shortcutCleanAppCleanerEnabled
        } returns mockDataStoreValue(enabledTools.contains(SDMTool.Type.APPCLEANER))
        every {
            generalSettings.shortcutCleanDeduplicatorEnabled
        } returns mockDataStoreValue(enabledTools.contains(SDMTool.Type.DEDUPLICATOR))

        coEvery { oneTapCleaner.runSingleTool(any(), any(), any()) } coAnswers {
            // The real cleaner fires onStarted only once the run is underway (pinned by
            // OneTapCleanerTest), so a rejected outcome never invokes it here either.
            if (outcome == OneTapCleaner.Outcome.Ran) thirdArg<suspend () -> Unit>().invoke()
            outcome
        }
    }

    private fun create() = CleanShortcutRouter(generalSettings, oneTapCleaner)

    @Test
    fun `an unknown tool name opens the app without cleaning`() = runTest {
        setup()
        create().route("nonsense") shouldBe CleanShortcutRouter.Route.OpenApp
        coVerify(exactly = 0) { oneTapCleaner.runSingleTool(any(), any(), any()) }
    }

    @Test
    fun `an absent tool name opens the app without cleaning`() = runTest {
        setup()
        create().route(null) shouldBe CleanShortcutRouter.Route.OpenApp
        coVerify(exactly = 0) { oneTapCleaner.runSingleTool(any(), any(), any()) }
    }

    @Test
    fun `a disabled tool opens the app without cleaning`() = runTest {
        // A shortcut pinned to the home screen and later switched off must not clean.
        setup(enabledTools = emptySet())
        OneTapCleaner.ONECLICK_TYPES.forEach { type ->
            create().route(type.name) shouldBe CleanShortcutRouter.Route.OpenApp
        }
        coVerify(exactly = 0) { oneTapCleaner.runSingleTool(any(), any(), any()) }
    }

    @Test
    fun `a non-Pro user is routed to the upgrade screen`() = runTest {
        setup(outcome = OneTapCleaner.Outcome.NotPro)
        create().route(SDMTool.Type.CORPSEFINDER.name) shouldBe CleanShortcutRouter.Route.OpenUpgrade
    }

    @Test
    fun `an already running clean opens the app so progress is visible`() = runTest {
        setup(outcome = OneTapCleaner.Outcome.AlreadyRunning)
        create().route(SDMTool.Type.CORPSEFINDER.name) shouldBe CleanShortcutRouter.Route.OpenApp
    }

    @Test
    fun `a clean that ran needs no navigation`() = runTest {
        setup(outcome = OneTapCleaner.Outcome.Ran)
        create().route(SDMTool.Type.CORPSEFINDER.name) shouldBe CleanShortcutRouter.Route.Nowhere
        coVerify(exactly = 1) {
            oneTapCleaner.runSingleTool(SDMTool.Type.CORPSEFINDER, shortcutMode = true, onStarted = any())
        }
    }

    @Test
    fun `the started hint fires for a real run`() = runTest {
        setup(outcome = OneTapCleaner.Outcome.Ran)
        var started = 0
        create().route(SDMTool.Type.APPCLEANER.name) { started++ }
        started shouldBe 1
    }

    @Test
    fun `the started hint does not fire for a rejected run`() = runTest {
        listOf(OneTapCleaner.Outcome.NotPro, OneTapCleaner.Outcome.AlreadyRunning).forEach { outcome ->
            setup(outcome = outcome)
            var started = 0
            create().route(SDMTool.Type.APPCLEANER.name) { started++ }
            started shouldBe 0
        }
    }

    @Test
    fun `the started hint does not fire when nothing is started`() = runTest {
        setup(enabledTools = emptySet())
        var started = 0
        create().route(SDMTool.Type.APPCLEANER.name) { started++ }
        create().route("nonsense") { started++ }
        started shouldBe 0
    }

    @Test
    fun `each tool is gated on its own opt-in`() = runTest {
        // A swapped setting mapping would let a disabled tool clean (or block an enabled one).
        OneTapCleaner.ONECLICK_TYPES.forEach { enabled ->
            setup(enabledTools = setOf(enabled))
            OneTapCleaner.ONECLICK_TYPES.forEach { requested ->
                val expected = when (requested) {
                    enabled -> CleanShortcutRouter.Route.Nowhere
                    else -> CleanShortcutRouter.Route.OpenApp
                }
                create().route(requested.name) shouldBe expected
            }
            coVerify(exactly = 1) { oneTapCleaner.runSingleTool(enabled, shortcutMode = true, onStarted = any()) }
        }
    }
}
