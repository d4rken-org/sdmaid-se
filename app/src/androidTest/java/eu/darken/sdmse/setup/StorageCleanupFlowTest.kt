package eu.darken.sdmse.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import eu.darken.sdmse.R
import eu.darken.sdmse.main.ui.MainActivity
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import testhelper.BaseAppFlowTest
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern
import eu.darken.sdmse.common.R as CommonR

@RunWith(AndroidJUnit4::class)
class StorageCleanupFlowTest : BaseAppFlowTest() {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pkg: String get() = context.packageName
    private val plantFile = Paths.get(PLANT_FILE)

    @Before
    fun resetLeftoverAccess() {
        shell("appops set $pkg GET_USAGE_STATS default")
        shell("pm revoke $pkg ${Manifest.permission.WRITE_SECURE_SETTINGS}")
        shell("rm -rf $PLANT_DIR")
    }

    @After
    fun removePlantedFiles() {
        shell("rm -rf $PLANT_DIR")
    }

    /** The only test that grants storage access; extend it instead of adding another, in this class or elsewhere. */
    @Test
    fun grantingStorageAccessHidesTheCardAndLetsTheDashboardDeleteJunk() {
        withClue(
            "Storage access already granted: another test in this run granted it (see testing.md), " +
                "or an existing $pkg install holds it (uninstall it)",
        ) {
            hasStorageAccess() shouldBe false
        }
        shell("mkdir -p $PLANT_DIR")
        shell("cp /system/etc/hosts $PLANT_FILE")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            walkOnboarding()
            awaitListItem(hasText(str(R.string.setup_manage_storage_card_body)))

            grantStorageThroughTheCard(scenario)
            pollUntil("storage access granted") { hasStorageAccess() }

            awaitNoListItem(hasText(str(R.string.setup_manage_storage_card_title)))
            // Root access stays incomplete, so its card proves the list is still Setup's.
            awaitListItem(hasText(str(R.string.setup_root_card_title)))
            Files.exists(plantFile) shouldBe true

            // Onboarding ends on Setup, pushed over the dashboard.
            val closeSetup = hasContentDescription(str(CommonR.string.general_close_action))
            awaitNode(closeSetup)
            composeRule.onNode(closeSetup).performClick()

            val scan = hasContentDescription(str(CommonR.string.general_scan_action))
            awaitNode(scan)
            composeRule.onNode(scan).performClick()

            val delete = hasContentDescription(str(CommonR.string.general_delete_action))
            awaitNode(delete)
            composeRule.onNode(delete).performClick()

            val confirm = hasText(str(CommonR.string.general_delete_action)) and hasAnyAncestor(isDialog())
            awaitNode(confirm)
            withClue("$PLANT_FILE deleted before the confirmation") { Files.exists(plantFile) shouldBe true }
            composeRule.onNode(confirm).performClick()
            composeRule.waitForIdle()

            pollUntil("$PLANT_FILE deleted") { Files.notExists(plantFile) }
        }
    }

    /** The way a user grants it, so the app's own handling of the result runs. */
    private fun grantStorageThroughTheCard(scenario: ActivityScenario<MainActivity>) {
        val grant = hasText(str(CommonR.string.general_grant_access_action)) and
            hasAnySibling(hasText(str(R.string.setup_manage_storage_card_body)))
        awaitListItem(grant)
        composeRule.onNode(grant).performClick()
        composeRule.waitForIdle()

        if (Build.VERSION.SDK_INT >= 30) {
            // The "All files access" page has a single switch.
            val allFilesSwitch = By.pkg(SETTINGS_PKG).checkable(true)
            val switch = device.wait(Until.findObject(allFilesSwitch), SYSTEM_UI_TIMEOUT_MS)
                ?: throw AssertionError("All files access switch not found")
            switch.click()
            device.wait(Until.hasObject(allFilesSwitch.checked(true)), SYSTEM_UI_TIMEOUT_MS) shouldBe true
            scenario.pressBackUntilResumed()
        } else {
            val allow = device.wait(Until.findObject(By.res(ALLOW_BUTTON)), SYSTEM_UI_TIMEOUT_MS)
                ?: throw AssertionError("Permission dialog's allow button not found")
            allow.click()
        }
    }

    private fun hasStorageAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        else -> isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
            isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val PLANT_DIR = "/sdcard/Download/sdmse-dashboard-flow"
        private const val PLANT_FILE = "$PLANT_DIR/desktop.ini"
        private const val SETTINGS_PKG = "com.android.settings"
        private val ALLOW_BUTTON = Pattern.compile(".*:id/permission_allow_button")
        private const val SYSTEM_UI_TIMEOUT_MS = 30_000L
    }
}
