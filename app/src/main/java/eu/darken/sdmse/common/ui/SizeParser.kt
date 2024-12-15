package eu.darken.sdmse.common.ui

import android.content.Context
import android.text.format.Formatter
import eu.darken.sdmse.common.debug.logging.log

class SizeParser(private val context: Context) {

    private val sizeUnitsRegex = Regex("(\\d+(?:[,.]\\d+)?)\\s*(\\w+)", RegexOption.IGNORE_CASE)
    private val sizeUnitsLocalized by lazy {
        val unitDelimiterRegex = Regex("\\s")
        val sizeSplitter: (Long, String) -> Pair<String, Long> = { size, fallback ->
            val unit = Formatter.formatShortFileSize(context, size).split(unitDelimiterRegex).lastOrNull() ?: fallback
            unit.uppercase() to size
        }
        mapOf(
            sizeSplitter(1L, "B"),
            sizeSplitter(1_000L, "kB"),
            sizeSplitter(1_000_000L, "MB"),
            sizeSplitter(1_000_000_000L, "GB"),
        ).also { log { "Size lookup map: $it" } }
    }

    fun parse(input: String): Long? {
        val match = sizeUnitsRegex.matchEntire(input.trim()) ?: return null
        val (value, unit) = match.destructured
        val factor = sizeUnitsLocalized[unit.uppercase()] ?: return null
        return (value.replace(',', '.').toDoubleOrNull()?.times(factor))?.toLong()?.also {
            log { "Parsed size '$input' to: $it" }
        }
    }
}