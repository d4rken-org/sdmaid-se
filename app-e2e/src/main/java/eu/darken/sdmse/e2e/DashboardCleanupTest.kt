package eu.darken.sdmse.e2e

import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Drives the unmodified, R8-minified app from outside its process: onboarding, then the dashboard's scan, delete
 * and confirmation against a planted junk file. What minification breaks on that path fails here.
 */
@RunWith(AndroidJUnit4::class)
class DashboardCleanupTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val appResources by lazy { instrumentation.context.packageManager.getResourcesForApplication(PKG) }
    private lateinit var startedAt: String

    @Before
    fun freshAppWithStorageAccess() {
        startedAt = shell("date +%s").trim()
        shell("pm clear $PKG")
        // Granted before the first launch, so the app's first data area build already covers shared storage.
        if (Build.VERSION.SDK_INT >= 30) {
            shell("appops set $PKG MANAGE_EXTERNAL_STORAGE allow")
        } else {
            shell("pm grant $PKG android.permission.WRITE_EXTERNAL_STORAGE")
            shell("pm grant $PKG android.permission.READ_EXTERNAL_STORAGE")
        }
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant $PKG android.permission.POST_NOTIFICATIONS")
        shell("rm -rf $PLANT_DIR")
        shell("mkdir -p $PLANT_DIR")
        shell("cp /system/etc/hosts $PLANT_FILE")
        assertTrue("Could not plant $PLANT_FILE", exists(PLANT_FILE))
    }

    @After
    fun cleanUp() {
        shell("am force-stop $PKG")
        shell("rm -rf $PLANT_DIR")
    }

    @Test
    fun scanDeleteAndConfirmRemovesPlantedJunk() {
        val launch = instrumentation.context.packageManager.getLaunchIntentForPackage(PKG)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.context.startActivity(launch)

        walkOnboarding()

        // Onboarding ends on Setup, pushed over the dashboard.
        await(By.desc(str("general_close_action"))).click()
        await(By.desc(str("general_scan_action"))).click()
        await(By.desc(str("general_delete_action")), SCAN_TIMEOUT_MS).click()

        val dialog = await(By.hasDescendant(By.text(str("dashboard_delete_all_message"))))
        assertTrue("$PLANT_FILE deleted before the confirmation", exists(PLANT_FILE))
        dialog.findObject(By.text(str("general_delete_action"))).click()

        pollUntil("$PLANT_FILE deleted") { !exists(PLANT_FILE) }
    }

    private fun walkOnboarding() {
        await(By.text(str("onboarding_welcome_title")))
        await(By.text(str("onboarding_welcome_continue_action"))).click()

        val versus = By.text(str("onboarding_versus_title"))
        val privacy = By.text(str("onboarding_privacy_title"))
        pollUntil("versus or privacy screen") { device.hasObject(versus) || device.hasObject(privacy) }
        if (device.hasObject(versus)) {
            await(By.text(str("onboarding_versus_continue_action"))).click()
        }
        await(privacy)
        // Keep network-driven content out of the flow.
        switchOff("motd_setting_enabled_label")
        if (BuildConfig.FLAVOR == "foss") switchOff("updatecheck_setting_enabled_label")
        await(By.text(str("onboarding_welcome_continue_action"))).click()

        await(By.text(str("onboarding_setup_continue_action")))
        switchOff("onboarding_setup_tours_enabled_label")
        await(By.text(str("onboarding_setup_continue_action"))).click()
    }

    private fun switchOff(labelName: String) {
        val row = By.checkable(true).hasDescendant(By.text(str(labelName)))
        val node = findScrolling(row)
        if (node.isChecked) node.click()
        pollUntil("$labelName off") { device.findObject(row)?.isChecked == false }
    }

    private fun findScrolling(selector: BySelector): UiObject2 {
        repeat(5) {
            device.findObject(selector)?.let { return it }
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.5f)
        }
        return await(selector)
    }

    private fun await(selector: BySelector, timeoutMs: Long = TIMEOUT_MS): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs) ?: fail("Not found: $selector")

    private fun pollUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (!condition()) {
            if (SystemClock.uptimeMillis() > deadline) fail("Timed out: $description")
            SystemClock.sleep(200)
        }
    }

    // A minification break usually surfaces as a crash or an error dialog; without them the failure only names a missing screen.
    private fun fail(message: String): Nothing {
        val screen = runCatching {
            val texts = device.findObjects(By.text(Pattern.compile("(?s).+"))).mapNotNull { runCatching { it.text }.getOrNull() }
            "Screen (${device.currentPackageName}): ${texts.joinToString(" | ")}"
        }.getOrElse { "Screen unavailable: $it" }
        val crashes = runCatching {
            val lines = shell("logcat -b crash -d -T $startedAt.0").lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) "No crashes logged" else lines.takeLast(40).joinToString("\n")
        }.getOrElse { "Crash buffer unavailable: $it" }
        throw AssertionError("$message\n$screen\nCrash buffer since the test started, all processes:\n$crashes")
    }

    private fun str(name: String): String {
        val id = appResources.getIdentifier(name, "string", PKG)
        if (id == 0) throw AssertionError("String resource '$name' not found in $PKG")
        return appResources.getString(id)
    }

    private fun exists(path: String): Boolean = shell("ls $path").trim() == path

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val PKG = "eu.darken.sdmse"
        private const val PLANT_DIR = "/sdcard/Download/sdmse-e2e"
        private const val PLANT_FILE = "$PLANT_DIR/desktop.ini"
        private const val TIMEOUT_MS = 30_000L
        private const val SCAN_TIMEOUT_MS = 120_000L
    }
}
