package testhelper

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import org.junit.Rule

abstract class BaseAppFlowTest : BaseUITest() {

    @get:Rule val composeRule = createEmptyComposeRule()

    protected fun str(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /**
     * Runs as the shell uid. Revoking a runtime permission or `MANAGE_EXTERNAL_STORAGE` kills the app process,
     * and with it this test. A default `UiAutomation` connection would suppress SD Maid's accessibility service.
     */
    protected fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    /** For system screens outside the app. Connects with the same flags as [shell]. */
    protected val device: UiDevice by lazy {
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /**
     * For waits while the app is in the background, where `composeRule.waitUntil` finds no hierarchy. It doesn't
     * advance Compose effects: after a click whose handling must happen first, call `composeRule.waitForIdle()`.
     */
    protected fun pollUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (!condition()) {
            if (SystemClock.uptimeMillis() > deadline) failWithScreen("Timed out: $description")
            SystemClock.sleep(100)
        }
    }

    /** Logs the on-screen UI hierarchy before failing; CI uploads each test's logcat. */
    protected fun failWithScreen(message: String): Nothing {
        val dump = ByteArrayOutputStream()
        runCatching { device.dumpWindowHierarchy(dump) }
            .onFailure { log(TAG, WARN) { "Screen dump failed: $it" } }
        log(TAG, WARN) { "Screen at failure ($message):" }
        dump.toString().lines().forEach { line -> line.chunked(1000).forEach { log(TAG, WARN) { it } } }
        throw AssertionError(message)
    }

    /** Walks a fresh install from the welcome screen into the onboarding Setup screen. */
    protected fun walkOnboarding() {
        awaitNode(hasText(str(R.string.onboarding_welcome_title)))
        composeRule.onNodeWithText(str(R.string.onboarding_welcome_continue_action)).performClick()

        val versus = hasText(str(R.string.onboarding_versus_title))
        val privacy = hasText(str(R.string.onboarding_privacy_title))
        awaitNode(versus or privacy)
        awaitGone(hasText(str(R.string.onboarding_welcome_title)))
        if (composeRule.onAllNodes(versus).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(str(R.string.onboarding_versus_continue_action)).performClick()
            awaitNode(privacy)
            awaitGone(versus)
        }
        // Disable MOTD and update checks before entering the dashboard.
        switchOff(R.string.motd_setting_enabled_label)
        if (BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.FOSS) {
            switchOff(R.string.updatecheck_setting_enabled_label)
        }
        composeRule.onNodeWithText(str(R.string.onboarding_welcome_continue_action)).performClick()

        awaitNode(hasText(str(R.string.onboarding_setup_continue_action)))
        awaitGone(hasText(str(R.string.onboarding_privacy_title)))
        // Keep guided tours out of the flow under test.
        switchOff(R.string.onboarding_setup_tours_enabled_label)
        composeRule.onNodeWithText(str(R.string.onboarding_setup_continue_action)).performClick()
        awaitGone(hasText(str(R.string.onboarding_setup_continue_action)))
    }

    /** Pauses and resumes the activity, delivering ON_RESUME. */
    protected fun ActivityScenario<*>.cycleResume() {
        moveToState(Lifecycle.State.STARTED)
        moveToState(Lifecycle.State.RESUMED)
    }

    /**
     * Returns from a system screen. On CI's API 36 image a single back press from Settings was lost while Settings
     * refreshed; a press landing after the app resumed would leave the app's screen, so a retry can't turn a failure
     * green.
     */
    protected fun ActivityScenario<*>.pressBackUntilResumed() = pollUntil("activity resumed") {
        if (state != Lifecycle.State.RESUMED) {
            shell("input keyevent KEYCODE_BACK")
            SystemClock.sleep(3000)
        }
        state == Lifecycle.State.RESUMED
    }

    protected fun switchOff(@StringRes label: Int) {
        val row = hasText(str(label)) and isToggleable()
        awaitNode(row)
        composeRule.onNode(row).performScrollTo().performClick()
        awaitNode(row and isOff())
    }

    /** Waits for an item of the screen's lazy list, scrolling to it; off-screen items are not in the tree. */
    protected fun awaitListItem(matcher: SemanticsMatcher) =
        composeRule.waitUntil("list item: ${matcher.description}", TIMEOUT_MS) {
            runCatching {
                composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
            }.isSuccess
        }

    /**
     * Waits until the list has been scrolled end to end without finding [matcher]. Also passes on any other
     * screen with a scrollable list, so follow it with an [awaitListItem] that pins the screen.
     */
    protected fun awaitNoListItem(matcher: SemanticsMatcher) =
        composeRule.waitUntil("no list item: ${matcher.description}", TIMEOUT_MS) {
            composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().size == 1 &&
                runCatching {
                    composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
                }.exceptionOrNull() is AssertionError
        }

    protected fun awaitNode(matcher: SemanticsMatcher) =
        composeRule.waitUntil(matcher.description, TIMEOUT_MS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }

    protected fun awaitGone(matcher: SemanticsMatcher) =
        composeRule.waitUntil("gone: ${matcher.description}", TIMEOUT_MS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
        }

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private val TAG = logTag("Test", "AppFlow")
    }
}
