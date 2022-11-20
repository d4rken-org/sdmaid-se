package eu.darken.sdmse.common.debug.logging

import android.os.Build
import android.util.Log
import kotlin.math.min

class LogCatLogger : Logging.Logger {

    override fun isLoggable(priority: Logging.Priority): Boolean = true

    override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {

        val trimmedTag = if (tag.length <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= 26) {
            tag
        } else {
            tag.substring(0, MAX_TAG_LENGTH)
        }

        if (message.length < MAX_LOG_LENGTH) {
            writeToLogcat(priority.intValue, trimmedTag, message)
            return
        }

        // Split by line, then ensure each line can fit into Log's maximum length.
        var i = 0
        val length = message.length
        while (i < length) {
            var newline = message.indexOf('\n', i)
            newline = if (newline != -1) newline else length
            do {
                val end = min(newline, i + MAX_LOG_LENGTH)
                val part = message.substring(i, end)
                writeToLogcat(priority.intValue, trimmedTag, part)
                i = end
            } while (i < newline)
            i++
        }
    }

    private fun writeToLogcat(priority: Int, tag: String, part: String) = when (priority) {
        Log.ASSERT -> Log.wtf(tag, part)
        else -> Log.println(priority, tag, part)
    }

    companion object {
        private const val MAX_LOG_LENGTH = 4000
        private const val MAX_TAG_LENGTH = 23
    }
}