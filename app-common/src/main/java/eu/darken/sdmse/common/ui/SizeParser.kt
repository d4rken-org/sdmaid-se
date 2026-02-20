package eu.darken.sdmse.common.ui

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.Formatter
import eu.darken.sdmse.common.debug.logging.log
import java.text.DecimalFormatSymbols

class SizeParser(private val context: Context) {
    private val locale by lazy { context.resources.configuration.locales[0] }
    private val decimalSeperator by lazy { DecimalFormatSymbols(locale).decimalSeparator }
    private val sizeUnitsRegex by lazy {
        Regex(
            """(\p{Nd}+(?:[$decimalSeperator]\p{Nd}+)?)[^\p{Nd}\p{L}.]*([\p{L}.]+)""",
            RegexOption.IGNORE_CASE
        )
    }
    private val sizeUnitsLocalized by lazy {
        val unitRegex = Regex("""[\p{L}.]+$""")
        val extractUnit: ((Context, Long) -> String, Long, String) -> Pair<String, Long> = { formatter, size, fallback ->
            val formatted = formatter(context, size)
            val unit = unitRegex.find(formatted)?.value ?: fallback
            unit.uppercase() to size
        }
        buildMap {
            // Short-form units from formatShortFileSize (e.g., "B", "kB", "MB", "GB")
            listOf(1L to "B", 1_000L to "kB", 1_000_000L to "MB", 1_000_000_000L to "GB").forEach { (size, fallback) ->
                val (unit, mult) = extractUnit(Formatter::formatShortFileSize, size, fallback)
                put(unit, mult)
            }
            // Long-form units via ICU MeasureFormat (same formatter Settings app uses)
            val measureFormat = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE)
            val measureUnits = listOf(
                MeasureUnit.BYTE to 1L,
                MeasureUnit.KILOBYTE to 1_000L,
                MeasureUnit.MEGABYTE to 1_000_000L,
                MeasureUnit.GIGABYTE to 1_000_000_000L,
            )
            for ((measureUnit, multiplier) in measureUnits) {
                for (probeSize in listOf(0, 1, 2, 5)) {
                    val formatted = measureFormat.format(Measure(probeSize.toDouble(), measureUnit))
                    val unit = unitRegex.find(formatted)?.value ?: continue
                    put(unit.uppercase(), multiplier)
                }
            }
        }.also { log { "Size lookup map: $it" } }
    }

    private fun normalizeDigits(input: String): String = input.map {
        when {
            Character.isDigit(it) -> Character.getNumericValue(it).toString()
            else -> it.toString()
        }
    }.joinToString("")

    fun parse(input: String): Long? {
        val trimmed = input.trim()
        val match = sizeUnitsRegex.matchEntire(trimmed) ?: run {
            log { "No regex match for '$input' (hex=${trimmed.map { "%04x".format(it.code) }})" }
            return null
        }
        val (value, unit) = match.destructured
        val valueNormalized = normalizeDigits(value)
            .replace(decimalSeperator, '.')
            .toDoubleOrNull()
        val factor = sizeUnitsLocalized[unit.uppercase()] ?: run {
            log { "Unknown unit '$unit' in '$input'" }
            return null
        }
        return valueNormalized
            ?.times(factor)?.toLong()
            .also { log { "Parsed size '$input' to: $it Byte" } }
    }
}