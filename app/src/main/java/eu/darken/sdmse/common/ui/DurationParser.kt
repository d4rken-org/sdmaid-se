package eu.darken.sdmse.common.ui

import android.content.Context
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.getQuantityString2
import java.time.Duration
import kotlin.math.roundToLong
import kotlin.reflect.KFunction1

class DurationParser(private val context: Context) {


    private val ageSplitRegex = Regex(
        """\s*(\d+(?:[.,]\d+)?)\s*([^\d\s]+)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): Duration? {
        val normalizedInput = input.trim()
        val match = ageSplitRegex.matchEntire(normalizedInput) ?: return null
        val (valueRaw, unitRaw) = match.destructured

        val value = valueRaw
            .replace(',', '.')
            .toDoubleOrNull()
            ?.roundToLong() ?: return null

        val unit = unitRaw.lowercase()

        val localizedUnits: List<Pair<String, KFunction1<Long, Duration?>>> = listOf(
            context.getQuantityString2(R.plurals.general_age_hours, value.toInt()).lowercase() to Duration::ofHours,
            context.getQuantityString2(R.plurals.general_age_days, value.toInt()).lowercase() to Duration::ofDays
        )

        return localizedUnits
            .firstOrNull { (label, _) -> label.contains(unit) }
            ?.second
            ?.invoke(value)
    }
}