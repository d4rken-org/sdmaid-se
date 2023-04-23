package eu.darken.sdmse.common.debug.autoreport

import android.app.Application
import android.content.Context
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import com.getkeepsafe.relinker.ReLinker
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.App
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.SDMId
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.AutomaticBugReporter
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.autoreport.bugsnag.BugsnagErrorHandler
import eu.darken.sdmse.common.debug.autoreport.bugsnag.BugsnagLogger
import eu.darken.sdmse.common.debug.autoreport.bugsnag.NOPBugsnagErrorHandler
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class GooglePlayReporting @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val debugSettings: DebugSettings,
    private val sdmId: SDMId,
    private val bugsnagLogger: Provider<BugsnagLogger>,
    private val bugsnagErrorHandler: Provider<BugsnagErrorHandler>,
    private val nopBugsnagErrorHandler: Provider<NOPBugsnagErrorHandler>,
) : AutomaticBugReporter {

    override fun setup(application: Application) {
        val isEnabled = generalSettings.isBugReporterEnabled.valueBlocking
        log(TAG) { "setup(): isEnabled=$isEnabled" }

        if (isEnabled) {
            ReLinker
                .log { message -> log(App.TAG) { "ReLinker: $message" } }
                .loadLibrary(application, "bugsnag-plugin-android-anr")
        }
        try {
            val bugsnagConfig = Configuration.load(context).apply {
                if (generalSettings.isBugReporterEnabled.valueBlocking) {
                    Logging.install(bugsnagLogger.get())
                    setUser(sdmId.id, null, null)
                    autoTrackSessions = true
                    addOnError(bugsnagErrorHandler.get())
                    addMetadata("App", "buildFlavor", BuildConfigWrap.FLAVOR)
                    log(TAG) { "Bugsnag setup done!" }
                } else {
                    autoTrackSessions = false
                    addOnError(nopBugsnagErrorHandler.get())
                    log(TAG) { "Installing Bugsnag NOP error handler due to user opt-out!" }
                }
            }

            Bugsnag.start(context, bugsnagConfig)
            Bugs.reporter = this
        } catch (e: IllegalStateException) {
            log(TAG) { "Bugsnag API Key not configured." }
        }
    }

    override fun leaveBreadCrumb(crumb: String) {
        Bugsnag.leaveBreadcrumb(crumb)
    }

    override fun notify(throwable: Throwable) {
        Bugsnag.notify(throwable)
    }

    companion object {
        private val TAG = logTag("Debug", "GooglePlayReporting")
    }
}