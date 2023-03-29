package eu.darken.sdmse.common.debug

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

object Bugs {
    var reporter: AutomaticBugReporter? = null
    fun report(exception: Exception) {
        log(TAG, VERBOSE) { "Reporting $exception" }

        reporter?.notify(exception) ?: run {
            log(TAG, WARN) { "Bug tracking not initialized yet." }
        }
    }

    var isDryRun = false
    var isTrace = false
    var isDebug = false

    private val TAG = logTag("Debug", "Bugs")
}