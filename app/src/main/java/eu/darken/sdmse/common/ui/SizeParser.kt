package eu.darken.sdmse.common.ui

import android.content.Context
import android.text.format.Formatter
import eu.darken.sdmse.common.debug.logging.log
import java.text.DecimalFormatSymbols

class SizeParser(private val context: Context) {
    private val locale by lazy { context.resources.configuration.locales[0] }
    private val decimalSeperator by lazy { DecimalFormatSymbols(locale).decimalSeparator }
    private val sizeUnitsRegex by lazy {
        Regex(
            """(\p{Nd}+(?:[$decimalSeperator]\p{Nd}+)?)\s*([\p{L}.]+)""",
            RegexOption.IGNORE_CASE
        )
    }
    private val sizeUnitsLocalized by lazy {
        // Extract trailing letters/dots as the unit, handles locales without space between number and unit
        val unitRegex = Regex("""[\p{L}.]+$""")
        val sizeSplitter: (Long, String) -> Pair<String, Long> = { size, fallback ->
            val formatted = Formatter.formatShortFileSize(context, size)
            val unit = unitRegex.find(formatted)?.value ?: fallback
            unit.uppercase() to size
        }
        mapOf(
            sizeSplitter(1L, "B"),
            sizeSplitter(1_000L, "kB"),
            sizeSplitter(1_000_000L, "MB"),
            sizeSplitter(1_000_000_000L, "GB"),
        ).also { log { "Size lookup map: $it" } }
    }

    private fun normalizeDigits(input: String): String = input.map {
        when {
            Character.isDigit(it) -> Character.getNumericValue(it).toString()
            else -> it.toString()
        }
    }.joinToString("")

    fun parse(input: String): Long? {
        val match = sizeUnitsRegex.matchEntire(input.trim()) ?: return null
        val (value, unit) = match.destructured
        val valueNormalized = normalizeDigits(value)
            .replace(decimalSeperator, '.')
            .toDoubleOrNull()
        val factor = sizeUnitsLocalized[unit.uppercase()] ?: return null
        return valueNormalized
            ?.times(factor)?.toLong()
            .also { log { "Parsed size '$input' to: $it Byte" } }
    }
}