package eu.darken.sdmse.stats.ui

import android.content.Context
import eu.darken.sdmse.common.stats.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object InstallElapsed {
    enum class Span { DAYS, WEEKS, MONTHS, YEARS }
    data class Value(val span: Span, val count: Int)

    /**
     * Largest sensible unit elapsed between [installedAt] and [now], or null below a full day —
     * the caller renders no caption at all then rather than claim time that hasn't passed.
     */
    fun of(installedAt: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Value? {
        // Both guards are needed. The duration check stops "1 day" appearing minutes after a
        // late-evening install once the clock rolls past midnight, and absorbs a backwards-
        // corrected device clock (negative duration). The calendar check stops a "0 days" result
        // on a 25-hour DST fall-back day, where 24h of real time can land on the same local date.
        if (Duration.between(installedAt, now) < Duration.ofDays(1)) return null
        val start = LocalDate.ofInstant(installedAt, zone)
        val today = LocalDate.ofInstant(now, zone)
        if (!start.isBefore(today)) return null

        val years = wholeUnitsUntil(start, today, ChronoUnit.YEARS, LocalDate::plusYears)
        if (years >= 1) return Value(Span.YEARS, years.toInt())

        val months = wholeUnitsUntil(start, today, ChronoUnit.MONTHS, LocalDate::plusMonths)
        if (months >= 1) return Value(Span.MONTHS, months.toInt())

        val days = ChronoUnit.DAYS.between(start, today)
        return if (days >= 7) Value(Span.WEEKS, (days / 7).toInt()) else Value(Span.DAYS, days.toInt())
    }

    // ChronoUnit.YEARS/MONTHS.between() compares day-of-month numerically, so it under-counts by
    // exactly one whenever the end date is a clamped month-end: 2024-02-29 -> 2025-02-28 measures
    // as 11 months (not 1 year), and 2025-01-31 -> 2025-02-28 as 0 months (not 1). plusYears /
    // plusMonths clamp month-ends the same way CurriculumVitae.anniversaryOccurrenceOf does (a
    // Feb-29 install has its anniversary on Feb 28 in non-leap years), so re-test the next-higher
    // count against the actual date. Day-of-month truncation can only ever lose one unit, so a
    // single fixup step is exact.
    private fun wholeUnitsUntil(
        start: LocalDate,
        today: LocalDate,
        unit: ChronoUnit,
        plus: (LocalDate, Long) -> LocalDate,
    ): Long {
        val approx = unit.between(start, today)
        return if (!plus(start, approx + 1).isAfter(today)) approx + 1 else approx
    }

    fun format(context: Context, value: Value): String = context.resources.getQuantityString(
        when (value.span) {
            Span.DAYS -> R.plurals.stats_dash_since_days
            Span.WEEKS -> R.plurals.stats_dash_since_weeks
            Span.MONTHS -> R.plurals.stats_dash_since_months
            Span.YEARS -> R.plurals.stats_dash_since_years
        },
        value.count,
        value.count,
    )
}
