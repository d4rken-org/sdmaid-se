package eu.darken.sdmse.common.debug

import android.app.Application
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

object Bugs {
    var reporter: AutomaticBugReporter? = object : AutomaticBugReporter {
        override fun setup(application: Application) {
            log(TAG, INFO) { "Bug reporting is NOOP." }
        }

        override fun leaveBreadCrumb(crumb: String) { /* NOOP */
        }

        override fun notify(throwable: Throwable) { /* NOOP */
        }

    }

    fun report(exception: Exception) {
        log(TAG, VERBOSE) { "Reporting $exception" }

        reporter?.notify(exception) ?: run {
            log(TAG, WARN) { "Bug tracking not initialized yet." }
        }
    }

    fun leaveBreadCrumb(crumb: String) {
        log(TAG, VERBOSE) { "Leaving crumb $crumb" }

        reporter?.leaveBreadCrumb(crumb) ?: run {
            log(TAG, WARN) { "Bug tracking not initialized yet." }
        }
    }

    var isDryRun = false
    var isDebug = false
    var isTrace = false

    var processTag: String = "Default"

    private val TAG = logTag("Debug", "Bugs")
}