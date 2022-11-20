package eu.darken.sdmse

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.AutomaticBugReporter
import eu.darken.sdmse.common.debug.logging.*
import javax.inject.Inject

@HiltAndroidApp
open class App : Application() {

    @Inject lateinit var bugReporter: AutomaticBugReporter

    override fun onCreate() {
        super.onCreate()
        if (BuildConfigWrap.DEBUG) {
            Logging.install(LogCatLogger())
            log(TAG) { "BuildConfig.DEBUG=true" }
        }

        bugReporter.setup(this)

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }
    }

    companion object {
        internal val TAG = logTag("SDMSE")
    }
}
