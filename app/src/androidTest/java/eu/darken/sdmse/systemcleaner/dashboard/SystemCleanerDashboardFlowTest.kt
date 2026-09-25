package eu.darken.sdmse.systemcleaner.dashboard

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import eu.darken.sdmse.common.R as CommonR

/**
 * Must run after `eu.darken.sdmse.setup.SetupDetectionTest`: on API 30+ the storage access granted here survives the
 * orchestrator's clear, and that test needs to start without it.
 */
@RunWith(AndroidJUnit4::class)
class SystemCleanerDashboardFlowTest : BaseAppFlowTest() {

    private val pkg: String get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName
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

    @Test
    fun dashboardScanAndDeleteRemovesAPlantedWindowsFile() {
        if (Build.VERSION.SDK_INT >= 30) {
            shell("appops set $pkg MANAGE_EXTERNAL_STORAGE allow")
        } else {
            shell("pm grant $pkg ${Manifest.permission.WRITE_EXTERNAL_STORAGE}")
            shell("pm grant $pkg ${Manifest.permission.READ_EXTERNAL_STORAGE}")
        }
        shell("mkdir -p $PLANT_DIR")
        shell("cp /system/etc/hosts $PLANT_FILE")
        Files.exists(plantFile) shouldBe true

        ActivityScenario.launch(MainActivity::class.java).use {
            walkOnboarding()
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

    companion object {
        private const val PLANT_DIR = "/sdcard/Download/sdmse-dashboard-flow"
        private const val PLANT_FILE = "$PLANT_DIR/desktop.ini"
    }
}
