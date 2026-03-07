package eu.darken.sdmse.common.debug.autoreport

import android.app.Application
import eu.darken.sdmse.common.debug.AutomaticBugReporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePlayReporting @Inject constructor() : AutomaticBugReporter {

    override fun setup(application: Application) {
        // NOOP
    }

    override fun leaveBreadCrumb(crumb: String) {
        // NOOP
    }

    override fun notify(throwable: Throwable) {
        // NOOP
    }
}
