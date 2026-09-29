package eu.darken.sdmse.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Creates a user's state in an older release and checks it after an in-place update. The two phases run as
 * separate instrumentation runs with the app swapped in between, see tools/e2e/upgrade-test.sh.
 *
 * Several stores fall back to defaults, or delete their file, when they can't decode what an older build wrote,
 * so every check is on a value that differs from the default.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeTest {

    private val app = SdmApp()

    @Test
    fun beforeUpgrade() {
        app.resetWithStorageAccess()
        app.launch()
        app.walkOnboarding()
        app.tap(app.desc("general_close_action"))

        openSettings()
        app.tapScrolling(app.text("systemcleaner_tool_name"))
        app.setSettingsSwitch(THUMBNAILS_FILTER, checked = true)
        app.navigateUp()

        app.tapScrolling(app.text("general_settings_label"))
        app.tapScrolling(app.text("dashboard_card_config_title"))
        val analyzerSwitch = app.switchBesideText(app.text(HIDDEN_CARD))
        if (analyzerSwitch.isChecked) analyzerSwitch.click()
        app.pollUntil("$HIDDEN_CARD card hidden") { !app.switchBesideText(app.text(HIDDEN_CARD)).isChecked }
        app.navigateUp()
        app.navigateUp()

        app.tapScrolling(app.text("exclusion_manager_title"))
        app.tap(app.desc("exclusion_create_action"))
        app.tap(app.text("exclusion_type_segment"))
        app.await(By.clazz("android.widget.EditText")).text = SEGMENT
        app.tap(app.desc("general_save_action"))
        app.await(By.text(SEGMENT))
        showDefaultExclusions()
        app.findScrolling(By.text(REMOVED_DEFAULT)).longClick()
        app.tap(app.desc("general_delete_selected_action"))
        app.pollUntil("$REMOVED_DEFAULT removed") { !app.device.hasObject(By.text(REMOVED_DEFAULT)) }
        app.navigateUp()
        app.navigateUp()

        // Opening the manager for the first time creates the default schedule; renaming it tells it apart from a
        // recreated one, which would show the default name again.
        app.tapScrolling(app.text("general_manage_action"))
        app.tap(app.text("scheduler_edit_schedule_action"))
        app.await(By.clazz("android.widget.EditText").text(app.str("scheduler_schedule_default_name"))).text = SCHEDULE
        // The sheet's name field shows the new name too, so wait for the sheet itself to close.
        // Releases before the sheet fix can open this sheet too low, with Save's lower half under a 3-button nav bar.
        app.tapTopEdge(By.clickable(true).hasDescendant(app.text("general_save_action")))
        app.pollUntil("schedule sheet closed") { !app.device.hasObject(By.clazz("android.widget.EditText")) }
        app.await(By.text(SCHEDULE))
        app.navigateUp()

        // Prove this build kept the state before the update replaces it.
        app.forceStop()
        app.launch()
        assertState()
    }

    @Test
    fun afterUpgrade() {
        app.launch()
        assertState()

        val junk = app.plantJunk()
        try {
            app.scanDeleteAndConfirm(junk)
        } finally {
            app.removeJunk()
        }
    }

    private fun assertState() {
        // A completed onboarding starts on the dashboard.
        app.await(app.desc("general_scan_action"))
        app.findScrolling(app.text("scheduler_label"))
        app.assertAbsentScrolling(app.text(HIDDEN_CARD), "the hidden $HIDDEN_CARD card")

        openSettings()
        app.tapScrolling(app.text("systemcleaner_tool_name"))
        app.awaitSettingsSwitch(THUMBNAILS_FILTER, checked = true)
        app.navigateUp()

        app.tapScrolling(app.text("exclusion_manager_title"))
        app.await(By.text(SEGMENT))
        showDefaultExclusions()
        app.findScrolling(By.text(KEPT_DEFAULT))
        app.assertAbsentScrolling(By.text(REMOVED_DEFAULT), "the removed default exclusion $REMOVED_DEFAULT")
        app.navigateUp()
        app.navigateUp()

        app.tapScrolling(app.text("general_manage_action"))
        app.await(By.text(SCHEDULE))
        app.navigateUp()
        app.await(app.text("scheduler_label"))
        // Back from the scheduler the dashboard is still scrolled to its bottom card.
        app.scrollToTopUntil(app.desc("general_scan_action"))
    }

    private fun openSettings() {
        app.tap(app.desc("general_settings_title"))
        app.await(app.text("general_settings_title"))
    }

    // Not persisted: the list starts without defaults every time it opens.
    private fun showDefaultExclusions() {
        app.tap(app.desc("general_options_label"))
        app.tap(app.text("exclusion_show_defaults_action"))
    }

    companion object {
        private const val THUMBNAILS_FILTER = "systemcleaner_filter_thumbnails_label"
        private const val HIDDEN_CARD = "analyzer_tool_name"
        private const val SEGMENT = "sdmse-e2e-seg"
        private const val REMOVED_DEFAULT = "/data/rootfs"
        private const val KEPT_DEFAULT = "com.starfinanz.mobile.android.pushtan"
        private const val SCHEDULE = "sdmse-e2e-schedule"
    }
}
