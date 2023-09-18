package eu.darken.sdmse.common.debug.autoreport

import android.app.Application
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.SDMId
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.AutomaticBugReporter
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePlayReporting @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val debugSettings: DebugSettings,
    private val sdmId: SDMId,
) : AutomaticBugReporter {

    override fun setup(application: Application) {
        val isEnabled = generalSettings.isBugReporterEnabled.valueBlocking
        log(TAG) { "setup(): isEnabled=$isEnabled" }

        // NOOP
    }

    override fun leaveBreadCrumb(crumb: String) {
        // NOOP
    }

    override fun notify(throwable: Throwable) {
        // NOOP
    }

    companion object {
        private val TAG = logTag("Debug", "GooglePlayReporting")
    }
}