package eu.darken.sdmse.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the unmodified, R8-minified app from outside its process: onboarding, then the dashboard's scan, delete
 * and confirmation against a planted junk file. What minification breaks on that path fails here.
 */
@RunWith(AndroidJUnit4::class)
class DashboardCleanupTest {

    private val app = SdmApp()
    private lateinit var junk: String

    @Before
    fun freshAppWithStorageAccess() {
        app.resetWithStorageAccess()
        junk = app.plantJunk()
    }

    @After
    fun cleanUp() {
        app.forceStop()
        app.removeJunk()
    }

    @Test
    fun scanDeleteAndConfirmRemovesPlantedJunk() {
        app.launch()
        app.walkOnboarding()

        // Onboarding ends on Setup, pushed over the dashboard.
        app.tap(app.desc("general_close_action"))
        app.scanDeleteAndConfirm(junk)
    }
}
