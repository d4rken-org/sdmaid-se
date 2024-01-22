package eu.darken.sdmse.common

import eu.darken.sdmse.common.debug.logging.Logging
import java.time.Duration
import java.time.Instant

class JUnitLogger(private val minLogLevel: Logging.Priority = Logging.Priority.VERBOSE) : Logging.Logger {
    private val startTime = Instant.now()

    override fun isLoggable(priority: Logging.Priority): Boolean = priority.intValue >= minLogLevel.intValue

    override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
        val now = Instant.now()
        val seconds = Duration.between(startTime, now).seconds
        println("${now.toEpochMilli()} ($seconds) ${priority.shortLabel}/$tag: $message")
    }

}
