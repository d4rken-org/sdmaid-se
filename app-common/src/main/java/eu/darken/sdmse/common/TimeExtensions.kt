package eu.darken.sdmse.common

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

fun OffsetDateTime.toSystemTimezone(): ZonedDateTime = this
    .atZoneSameInstant(ZoneId.systemDefault())

fun Instant.toSystemTimezone(): ZonedDateTime = this
    .atZone(ZoneId.systemDefault())

fun Instant.toUtcTimezone(): ZonedDateTime = this
    .atZone(ZoneId.of("UTC"))

/**
 * Returns the given duration in a human-friendly format. For example,
 * "4 minutes" or "1 second". Returns only the largest meaningful unit of time,
 * from seconds up to hours.
 * <p>
 * You can use abbrev to specify a preference for abbreviations (but note that some
 * locales may not have abbreviations). Use LENGTH_LONG for the full spelling (e.g. "2 hours"),
 * LENGTH_SHORT for the abbreviated spelling if available (e.g. "2 hr"), and LENGTH_SHORTEST for
 * the briefest form available (e.g. "2h").
 */
fun Duration.formatDuration(abbrev: Int = DateUtils.LENGTH_SHORT): String {
    val width = when (abbrev) {
        DateUtils.LENGTH_LONG -> MeasureFormat.FormatWidth.WIDE
        DateUtils.LENGTH_SHORT, DateUtils.LENGTH_SHORTER, DateUtils.LENGTH_MEDIUM -> MeasureFormat.FormatWidth.SHORT
        DateUtils.LENGTH_SHORTEST -> MeasureFormat.FormatWidth.NARROW
        else -> MeasureFormat.FormatWidth.WIDE
    }
    val millis = toMillis()
    val formatter = MeasureFormat.getInstance(Locale.getDefault(), width)
    if (millis >= DateUtils.HOUR_IN_MILLIS) {
        val hours = ((millis + 1800000) / DateUtils.HOUR_IN_MILLIS).toInt()
        return formatter.format(Measure(hours, MeasureUnit.HOUR))
    } else if (millis >= DateUtils.MINUTE_IN_MILLIS) {
        val minutes = ((millis + 30000) / DateUtils.MINUTE_IN_MILLIS).toInt()
        return formatter.format(Measure(minutes, MeasureUnit.MINUTE))
    } else {
        val seconds = ((millis + 500) / DateUtils.SECOND_IN_MILLIS).toInt()
        return formatter.format(Measure(seconds, MeasureUnit.SECOND))
    }
}