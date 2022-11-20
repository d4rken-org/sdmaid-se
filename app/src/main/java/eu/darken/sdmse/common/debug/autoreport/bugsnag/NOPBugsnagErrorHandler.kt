package eu.darken.sdmse.common.debug.autoreport.bugsnag

import com.bugsnag.android.Event
import com.bugsnag.android.OnErrorCallback
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NOPBugsnagErrorHandler @Inject constructor() : OnErrorCallback {

    override fun onError(event: Event): Boolean {
        log(WARN) { "Error, but skipping bugsnag due to user opt-out: ${event.originalError?.asLog()}" }
        return false
    }

}