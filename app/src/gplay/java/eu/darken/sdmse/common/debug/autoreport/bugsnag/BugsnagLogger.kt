package eu.darken.sdmse.common.debug.autoreport.bugsnag

import com.bugsnag.android.Event
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority
import eu.darken.sdmse.common.debug.logging.asLog
import java.lang.String.format
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BugsnagLogger @Inject constructor() : Logging.Logger {

    override fun isLoggable(priority: Priority): Boolean = priority.intValue >= Priority.DEBUG.intValue

    // Adding one to the initial size accounts for the add before remove.
    private val buffer: Deque<String> = ArrayDeque(BUFFER_SIZE + 1)

    override fun log(priority: Priority, tag: String, message: String, metaData: Map<String, Any>?) {
        val line = "${System.currentTimeMillis()} ${priority.toLabel()}/$tag: $message"
        synchronized(buffer) {
            buffer.addLast(line)
            if (buffer.size > BUFFER_SIZE) {
                buffer.removeFirst()
            }
        }
    }

    fun injectLog(event: Event) {
        synchronized(buffer) {
            var i = 100
            buffer.forEach { event.addMetadata("Log", format(Locale.ROOT, "%03d", i++), it) }
            event.addMetadata("Log", format(Locale.ROOT, "%03d", i), event.originalError?.asLog())
        }
    }

    companion object {
        private const val BUFFER_SIZE = 200

        private fun Priority.toLabel(): String = when (this) {
            Priority.VERBOSE -> "V"
            Priority.DEBUG -> "D"
            Priority.INFO -> "I"
            Priority.WARN -> "W"
            Priority.ERROR -> "E"
            Priority.ASSERT -> "WTF"
        }
    }
}
