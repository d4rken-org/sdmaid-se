package eu.darken.sdmse.e2e

import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

/** Drives the installed app from outside its process; screens are matched by the app's own string resources. */
class SdmApp {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val appResources by lazy { instrumentation.context.packageManager.getResourcesForApplication(PKG) }
    private val startedAt = shell("date +%s").trim()

    fun resetWithStorageAccess() {
        shell("pm clear $PKG")
        // Granted before the first launch, so the app's first data area build already covers shared storage.
        if (Build.VERSION.SDK_INT >= 30) {
            shell("appops set $PKG MANAGE_EXTERNAL_STORAGE allow")
        } else {
            shell("pm grant $PKG android.permission.WRITE_EXTERNAL_STORAGE")
            shell("pm grant $PKG android.permission.READ_EXTERNAL_STORAGE")
        }
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant $PKG android.permission.POST_NOTIFICATIONS")
    }

    fun launch() {
        val intent = instrumentation.context.packageManager.getLaunchIntentForPackage(PKG)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.context.startActivity(intent)
    }

    fun forceStop() {
        shell("am force-stop $PKG")
    }

    fun walkOnboarding() {
        await(text("onboarding_welcome_title"))
        tap(text("onboarding_welcome_continue_action"))

        val versus = text("onboarding_versus_title")
        val privacy = text("onboarding_privacy_title")
        pollUntil("versus or privacy screen") { device.hasObject(versus) || device.hasObject(privacy) }
        if (device.hasObject(versus)) {
            tap(text("onboarding_versus_continue_action"))
        }
        await(privacy)
        // Keep network-driven content out of the flow.
        switchOff("motd_setting_enabled_label")
        if (BuildConfig.FLAVOR == "foss") switchOff("updatecheck_setting_enabled_label")
        tap(text("onboarding_welcome_continue_action"))

        await(text("onboarding_setup_continue_action"))
        switchOff("onboarding_setup_tours_enabled_label")
        tap(text("onboarding_setup_continue_action"))
    }

    /** Plants a file the dashboard's delete-all removes; returns its path. */
    fun plantJunk(): String {
        shell("rm -rf $PLANT_DIR")
        shell("mkdir -p $PLANT_DIR")
        shell("cp /system/etc/hosts $PLANT_FILE")
        if (!exists(PLANT_FILE)) throw AssertionError("Could not plant $PLANT_FILE")
        return PLANT_FILE
    }

    fun removeJunk() {
        shell("rm -rf $PLANT_DIR")
    }

    /** From the dashboard: scan, delete, confirm, and wait until [junk] is gone. */
    fun scanDeleteAndConfirm(junk: String) {
        // The action button stays in the tree while the bar is scrolled away, but disabled.
        scrollToTop()
        tap(desc("general_scan_action").enabled(true))
        await(desc("general_delete_action"), SCAN_TIMEOUT_MS)
        scrollToTop()
        tap(desc("general_delete_action").enabled(true))

        val dialog = await(By.hasDescendant(text("dashboard_delete_all_message")))
        if (!exists(junk)) fail("$junk deleted before the confirmation")
        dialog.findObject(text("general_delete_action")).click()

        pollUntil("$junk deleted") { !exists(junk) }
    }

    fun text(name: String): BySelector = By.text(str(name))

    fun desc(name: String): BySelector = By.desc(str(name))

    /** An onboarding toggle row, which is itself the checkable node. */
    fun switchOff(labelName: String) {
        val row = By.checkable(true).hasDescendant(text(labelName))
        val node = findScrolling(row)
        if (node.isChecked) node.click()
        pollUntil("$labelName off") { device.findObject(row)?.isChecked == false }
    }

    /** A settings row: the clickable row carries the label, the Switch inside it the state. */
    fun setSettingsSwitch(labelName: String, checked: Boolean) {
        val row = By.clickable(true).hasDescendant(text(labelName))
        if (settingsSwitch(findScrolling(row)).isChecked != checked) tapScrolling(row)
        awaitSettingsSwitch(labelName, checked)
    }

    // Settings screens render their default state until their flows emit, so a single read can see the default.
    fun awaitSettingsSwitch(labelName: String, checked: Boolean) {
        val row = By.clickable(true).hasDescendant(text(labelName))
        findScrolling(row)
        pollUntil("$labelName ${if (checked) "on" else "off"}") {
            device.findObject(row)?.let { settingsSwitch(it).isChecked } == checked
        }
    }

    private fun settingsSwitch(row: UiObject2): UiObject2 =
        if (row.isCheckable) row else row.findObject(By.checkable(true)) ?: fail("No switch in $row")

    /** The Switch on the same line as [label], for rows where they are siblings rather than parent and child. */
    fun switchBesideText(label: BySelector): UiObject2 {
        val labelBounds = findScrolling(label).visibleBounds
        return device.findObjects(By.checkable(true))
            .firstOrNull { it.visibleBounds.centerY() in labelBounds.top..labelBounds.bottom }
            ?: fail("No switch beside $label")
    }

    fun navigateUp() {
        tap(desc("general_navigate_up_action"))
    }

    fun findScrolling(selector: BySelector): UiObject2 {
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            repeat(8) {
                device.findObject(selector)?.let { return it }
                scrollList(direction, 0.5f)
            }
        }
        return await(selector)
    }

    /** Lazy lists only expose what is on screen, so absence is checked on every page from the top. */
    fun assertAbsentScrolling(selector: BySelector, description: String) {
        scrollToTop()
        var more = true
        while (true) {
            if (device.hasObject(selector)) fail("Still present: $description")
            if (!more) break
            // False once this scroll reached the end, which can still have revealed a page, so that page is checked too.
            more = scrollList(Direction.DOWN, 0.8f)
        }
        scrollToTop()
    }

    // Scrolling down hides the dashboard's bottom bar and disables its actions.
    fun scrollToTop() {
        scrollList(Direction.UP, 10f)
    }

    /** Scrolls the screen's list, found fresh each time; false once it can't scroll further or there is none. */
    private fun scrollList(direction: Direction, percent: Float): Boolean {
        repeat(STALE_RETRIES) {
            try {
                return device.findObject(By.scrollable(true))?.scroll(direction, percent) == true
            } catch (_: StaleObjectException) {
                SystemClock.sleep(200)
            }
        }
        return device.findObject(By.scrollable(true))?.scroll(direction, percent) == true
    }

    // Compose recycles nodes on recomposition, so a node found a moment ago can be stale by the time it is clicked.
    fun tap(selector: BySelector, timeoutMs: Long = TIMEOUT_MS) = retryStale { await(selector, timeoutMs).click() }

    fun tapScrolling(selector: BySelector) = retryStale { findScrolling(selector).click() }

    private fun retryStale(action: () -> Unit) {
        repeat(STALE_RETRIES) {
            try {
                return action()
            } catch (_: StaleObjectException) {
                SystemClock.sleep(200)
            }
        }
        action()
    }

    fun await(selector: BySelector, timeoutMs: Long = TIMEOUT_MS): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs) ?: fail("Not found: $selector")

    fun pollUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (!condition()) {
            if (SystemClock.uptimeMillis() > deadline) fail("Timed out: $description")
            SystemClock.sleep(200)
        }
    }

    // A minification break usually surfaces as a crash or an error dialog; without them the failure only names a missing screen.
    fun fail(message: String): Nothing {
        val screen = runCatching {
            val any = Pattern.compile("(?s).+")
            val texts = device.findObjects(By.text(any)).mapNotNull { runCatching { it.text }.getOrNull() }
            val descs = device.findObjects(By.desc(any)).mapNotNull { runCatching { it.contentDescription }.getOrNull() }
            "Screen (${device.currentPackageName}): ${texts.joinToString(" | ")}\nDescriptions: ${descs.joinToString(" | ")}"
        }.getOrElse { "Screen unavailable: $it" }
        val crashes = runCatching {
            val lines = shell("logcat -b crash -d -T $startedAt.0").lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) "No crashes logged" else lines.takeLast(40).joinToString("\n")
        }.getOrElse { "Crash buffer unavailable: $it" }
        // Bounds and enabled state of every node, for failures the text lists can't explain.
        val hierarchy = runCatching {
            ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString().take(HIERARCHY_MAX_CHARS)
        }.getOrElse { "unavailable: $it" }
        throw AssertionError(
            "$message\n$screen\nCrash buffer since the test started, all processes:\n$crashes\nWindow hierarchy:\n$hierarchy",
        )
    }

    fun str(name: String): String {
        val id = appResources.getIdentifier(name, "string", PKG)
        if (id == 0) throw AssertionError("String resource '$name' not found in $PKG")
        return appResources.getString(id)
    }

    fun exists(path: String): Boolean = shell("ls $path").trim() == path

    fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    companion object {
        const val PKG = "eu.darken.sdmse"
        const val TIMEOUT_MS = 30_000L
        const val SCAN_TIMEOUT_MS = 120_000L
        private const val STALE_RETRIES = 3
        private const val HIERARCHY_MAX_CHARS = 60_000
        private const val PLANT_DIR = "/sdcard/Download/sdmse-e2e"
        private const val PLANT_FILE = "$PLANT_DIR/desktop.ini"
    }
}
