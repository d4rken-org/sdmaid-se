package eu.darken.sdmse.common.ui

import android.content.Context
import eu.darken.sdmse.common.getQuantityString2
import java.time.Duration
import kotlin.math.roundToLong

class DurationParser(private val context: Context) {

    private val ageSplitRegex = Regex("(\\d+(\\.\\d+)?)\\s*(\\w+)", RegexOption.IGNORE_CASE)

    fun parse(input: String): Duration? {
        val (valueRaw, _, unit) = ageSplitRegex.matchEntire(input.trim())?.destructured ?: return null
        val value = valueRaw.toDoubleOrNull()?.roundToLong() ?: return null
        return when {
            context.getQuantityString2(
                eu.darken.sdmse.common.R.plurals.general_age_hours,
                value.toInt()
            ) == input -> {
                Duration.ofHours(value)
            }

            context.getQuantityString2(
                eu.darken.sdmse.common.R.plurals.general_age_days,
                value.toInt()
            ) == input -> {
                Duration.ofDays(value)
            }

            else -> null
        }
    }
}