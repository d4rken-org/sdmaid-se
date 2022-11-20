package eu.darken.sdmse.common.debug.autoreport

import android.content.Context
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.InstallId
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.autoreport.bugsnag.BugsnagErrorHandler
import eu.darken.sdmse.common.debug.autoreport.bugsnag.BugsnagLogger
import eu.darken.sdmse.common.debug.autoreport.bugsnag.NOPBugsnagErrorHandler
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AutoReporting @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bugReportSettings: DebugSettings,
    private val installId: InstallId,
    private val bugsnagLogger: Provider<BugsnagLogger>,
    private val bugsnagErrorHandler: Provider<BugsnagErrorHandler>,
    private val nopBugsnagErrorHandler: Provider<NOPBugsnagErrorHandler>,
) {

    fun setup() {
        val isEnabled = bugReportSettings.isAutoReportingEnabled.flow
        log(TAG) { "setup(): isEnabled=$isEnabled" }

        try {
            val bugsnagConfig = Configuration.load(context).apply {
                if (bugReportSettings.isAutoReportingEnabled.valueBlocking) {
                    Logging.install(bugsnagLogger.get())
                    setUser(installId.id, null, null)
                    autoTrackSessions = true
                    addOnError(bugsnagErrorHandler.get())
                    log(TAG) { "Bugsnag setup done!" }
                } else {
                    autoTrackSessions = false
                    addOnError(nopBugsnagErrorHandler.get())
                    log(TAG) { "Installing Bugsnag NOP error handler due to user opt-out!" }
                }
            }

            Bugsnag.start(context, bugsnagConfig)
            Bugs.ready = true
        } catch (e: IllegalStateException) {
            log(TAG) { "Bugsnag API Key not configured." }
        }
    }

    companion object {
        private val TAG = logTag("Debug", "AutoReporting")
    }
}