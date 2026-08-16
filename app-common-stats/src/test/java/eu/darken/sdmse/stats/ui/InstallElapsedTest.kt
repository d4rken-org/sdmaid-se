package eu.darken.sdmse.stats.ui

import eu.darken.sdmse.stats.ui.InstallElapsed.Span
import eu.darken.sdmse.stats.ui.InstallElapsed.Value
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class InstallElapsedTest : BaseTest() {

    private fun elapsed(start: String, today: String): Value? = InstallElapsed.of(
        installedAt = noon(start),
        now = noon(today),
        zone = ZoneOffset.UTC,
    )

    private fun noon(date: String): Instant = LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun `nothing to show minutes after the install`() {
        InstallElapsed.of(
            installedAt = Instant.parse("2025-03-10T12:00:00Z"),
            now = Instant.parse("2025-03-10T12:10:00Z"),
            zone = ZoneOffset.UTC,
        ).shouldBeNull()
    }

    @Test
    fun `crossing midnight is not a day`() {
        InstallElapsed.of(
            installedAt = Instant.parse("2025-03-09T23:50:00Z"),
            now = Instant.parse("2025-03-10T00:05:00Z"),
            zone = ZoneOffset.UTC,
        ).shouldBeNull()
    }

    @Test
    fun `23 hours across a date boundary is not a day`() {
        InstallElapsed.of(
            installedAt = Instant.parse("2025-03-09T12:00:00Z"),
            now = Instant.parse("2025-03-10T11:00:00Z"),
            zone = ZoneOffset.UTC,
        ).shouldBeNull()
    }

    @Test
    fun `an install date in the future shows nothing`() {
        InstallElapsed.of(
            installedAt = Instant.parse("2025-03-11T12:00:00Z"),
            now = Instant.parse("2025-03-10T12:00:00Z"),
            zone = ZoneOffset.UTC,
        ).shouldBeNull()
    }

    @Test
    fun `24 hours on a DST fall-back day stays on the same local date`() {
        val zone = ZoneId.of("America/New_York")
        val installedAt = LocalDateTime.parse("2025-11-02T00:30:00").atZone(zone).toInstant()
        InstallElapsed.of(
            installedAt = installedAt,
            now = installedAt.plus(Duration.ofHours(24)),
            zone = zone,
        ).shouldBeNull()
    }

    @Test
    fun `single day`() {
        elapsed("2025-03-09", "2025-03-10") shouldBe Value(Span.DAYS, 1)
    }

    @Test
    fun `days until a full week`() {
        elapsed("2025-03-04", "2025-03-10") shouldBe Value(Span.DAYS, 6)
    }

    @Test
    fun `seven days is one week`() {
        elapsed("2025-03-03", "2025-03-10") shouldBe Value(Span.WEEKS, 1)
    }

    @Test
    fun `weeks are truncated not rounded`() {
        elapsed("2025-02-25", "2025-03-10") shouldBe Value(Span.WEEKS, 1)
        elapsed("2025-02-24", "2025-03-10") shouldBe Value(Span.WEEKS, 2)
    }

    @Test
    fun `a month short of a month stays in weeks`() {
        elapsed("2025-01-01", "2025-01-31") shouldBe Value(Span.WEEKS, 4)
    }

    @Test
    fun `one month`() {
        elapsed("2025-01-01", "2025-02-01") shouldBe Value(Span.MONTHS, 1)
    }

    @Test
    fun `a month-end install rolls over on the clamped date`() {
        elapsed("2025-01-31", "2025-02-27") shouldBe Value(Span.WEEKS, 3)
        elapsed("2025-01-31", "2025-02-28") shouldBe Value(Span.MONTHS, 1)
    }

    @Test
    fun `eleven months is not a year`() {
        elapsed("2024-03-01", "2025-02-01") shouldBe Value(Span.MONTHS, 11)
    }

    @Test
    fun `a leap-day install turns one on February 28th`() {
        elapsed("2024-02-29", "2025-02-28") shouldBe Value(Span.YEARS, 1)
        elapsed("2024-02-29", "2025-03-01") shouldBe Value(Span.YEARS, 1)
    }

    @Test
    fun `two years`() {
        elapsed("2023-03-10", "2025-03-10") shouldBe Value(Span.YEARS, 2)
    }
}
