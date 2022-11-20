package eu.darken.sdmse.common.debug.logging

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Inspired by
 * https://github.com/PaulWoitaschek/Slimber
 * https://github.com/square/logcat
 * https://github.com/JakeWharton/timber
 */

object Logging {
    enum class Priority(
        val intValue: Int,
        val shortLabel: String
    ) {
        VERBOSE(2, "V"),
        DEBUG(3, "D"),
        INFO(4, "I"),
        WARN(5, "W"),
        ERROR(6, "E"),
        ASSERT(7, "WTF");

        companion object {
            fun fromAndroid(value: Int): Priority = values().firstOrNull { it.intValue == value } ?: ERROR
        }
    }

    interface Logger {
        fun isLoggable(priority: Priority): Boolean = true

        fun log(
            priority: Priority,
            tag: String,
            message: String,
            metaData: Map<String, Any>?
        )
    }

    private val internalLoggers = mutableListOf<Logger>()

    val loggers: List<Logger>
        get() = synchronized(internalLoggers) { internalLoggers.toList() }

    val hasReceivers: Boolean
        get() = synchronized(internalLoggers) {
            internalLoggers.isNotEmpty()
        }

    fun install(logger: Logger) {
        synchronized(internalLoggers) { internalLoggers.add(logger) }
        log { "Was installed $logger" }
    }

    fun remove(logger: Logger) {
        log { "Removing: $logger" }
        synchronized(internalLoggers) { internalLoggers.remove(logger) }
    }

    fun logInternal(
        tag: String,
        priority: Priority,
        metaData: Map<String, Any>?,
        message: String
    ) {
        val snapshot = synchronized(internalLoggers) { internalLoggers.toList() }
        snapshot
            .filter { it.isLoggable(priority) }
            .forEach {
                it.log(
                    priority = priority,
                    tag = tag,
                    metaData = metaData,
                    message = message
                )
            }
    }

    fun clearAll() {
        log { "Clearing all loggers" }
        synchronized(internalLoggers) { internalLoggers.clear() }
    }
}

inline fun Any.log(
    priority: Logging.Priority = Logging.Priority.DEBUG,
    metaData: Map<String, Any>? = null,
    message: () -> String,
) {
    if (Logging.hasReceivers) {
        Logging.logInternal(
            tag = logTag(logTagViaCallSite()),
            priority = priority,
            metaData = metaData,
            message = message(),
        )
    }
}

inline fun log(
    tag: String,
    priority: Logging.Priority = Logging.Priority.DEBUG,
    metaData: Map<String, Any>? = null,
    message: () -> String,
) {
    if (Logging.hasReceivers) {
        Logging.logInternal(
            tag = tag,
            priority = priority,
            metaData = metaData,
            message = message(),
        )
    }
}

fun Throwable.asLog(): String {
    val stringWriter = StringWriter(256)
    val printWriter = PrintWriter(stringWriter, false)
    printStackTrace(printWriter)
    printWriter.flush()
    return stringWriter.toString()
}

@PublishedApi
internal fun Any.logTagViaCallSite(): String {
    val javaClass = this::class.java
    val fullClassName = javaClass.name
    val outerClassName = fullClassName.substringBefore('$')
    val simplerOuterClassName = outerClassName.substringAfterLast('.')
    return if (simplerOuterClassName.isEmpty()) {
        fullClassName
    } else {
        simplerOuterClassName.removeSuffix("Kt")
    }
}
