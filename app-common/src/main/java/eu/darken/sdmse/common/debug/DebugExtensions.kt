package eu.darken.sdmse.common.debug

import eu.darken.sdmse.common.error.getStackTraceString


@Suppress("NOTHING_TO_INLINE")
inline fun traceCall() = CallTrace().getStackTraceString()

class CallTrace : Throwable()

fun CharSequence.toVisualString(): String = map { c ->
    when {
        c.isLetterOrDigit() -> c.toString()
        c.isWhitespace() -> c.toString()
        c in '!'..'~' -> c.toString()
        else -> "\\u%04x".format(c.code)
    }
}.joinToString("")

fun Collection<String>.toVisualStrings(): Collection<String> = map { it.toVisualString() }