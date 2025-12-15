package eu.darken.sdmse.scheduler.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ScheduleTest : BaseTest() {

    @Test fun `first execution calculation`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T12:00:00Z")
        )

        schedule.calcFirstExecutionAt(
            zone = ZoneId.of("UTC")
        ) shouldBe Instant.parse("2022-01-01T22:00:00Z")
        schedule.calcFirstExecutionAt(
            zone = ZoneId.of("GMT+1")
        ) shouldBe Instant.parse("2022-01-01T21:00:00Z")
        schedule.calcFirstExecutionAt(
            zone = ZoneId.of("GMT-1")
        ) shouldBe Instant.parse("2022-01-01T23:00:00Z")
    }

    @Test fun `time calculation - initial schedule`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T12:00:00Z")
        )

        schedule.calcExecutionEta(
            zone = ZoneId.of("UTC"),
            reschedule = false,
            now = Instant.parse("2022-01-01T12:00:00Z")
        ) shouldBe Duration.ofHours(10)
    }

    @Test fun `time calculation - initial schedule - timezone`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T12:00:00Z")
        )

        schedule.calcExecutionEta(
            zone = ZoneId.of("GMT+1"),
            reschedule = false,
            now = Instant.parse("2022-01-01T12:00:00Z")
        ) shouldBe Duration.ofHours(9)
    }

    @Test fun `time calculation - initial schedule - close call`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 2,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T22:00:00Z")
        )
        schedule.calcExecutionEta(
            zone = ZoneId.of("UTC"),
            reschedule = false,
            now = Instant.parse("2022-01-01T22:00:00Z")
        ) shouldBe Duration.ofMinutes(2)
    }

    @Test fun `time calculation - initial schedule - close call - timezone`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T22:00:00Z")
        )
        schedule.calcExecutionEta(
            zone = ZoneId.of("GMT-1"),
            reschedule = false,
            now = Instant.parse("2022-01-01T22:00:00Z")
        ) shouldBe Duration.ofHours(1)
    }

    @Test fun `time calculation - initial schedule - collision`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T22:00:00Z")
        )
        schedule.calcExecutionEta(
            zone = ZoneId.of("UTC"),
            reschedule = false,
            now = Instant.parse("2022-01-01T22:00:00Z")
        ) shouldBe Duration.ofDays(1)
    }

    @Test fun `time calculation - initial schedule - just too late`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T22:00:00Z")
        )
        schedule.calcExecutionEta(
            zone = ZoneId.of("UTC"),
            reschedule = false,
            now = Instant.parse("2022-01-01T22:01:00Z")
        ) shouldBe Duration.ofDays(1).minus(Duration.ofMinutes(1))
    }

    @Test fun `time calculation - reschedule`() {
        val schedule = Schedule(
            id = "id",
            hour = 14,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T12:00:00Z")
        )

        schedule.calcExecutionEta(
            zone = ZoneId.of("UTC"),
            reschedule = true,
            now = Instant.parse("2022-01-02T14:05:00Z")
        ) shouldBe Duration.ofHours(24).minusMinutes(5)
    }

    @Test fun `time calculation - reschedule - premature`() {
        val schedule = Schedule(
            id = "id",
            hour = 12,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T11:00:00Z")
        )

        schedule.calcExecutionEta(
            zone = ZoneId.of("UTC"),
            reschedule = true,
            now = Instant.parse("2022-01-02T11:55:00Z")
        ) shouldBe Duration.ofHours(24).plusMinutes(5)
    }

    @Test fun `time calculation - reschedule - premature - timezone`() {
        val schedule = Schedule(
            id = "id",
            hour = 12,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T11:00:00Z")
        )

        // 12 hour safety margin causes a reschedule to go to the next day
        schedule.calcExecutionEta(
            zone = ZoneId.of("GMT-1"),
            reschedule = true,
            now = Instant.parse("2022-01-02T11:55:00Z")
        ) shouldBe Duration.ofHours(25).plusMinutes(5)
    }

    @Test fun `stored timezone is used for calculation`() {
        // Schedule with stored timezone Europe/Berlin (UTC+1 in winter)
        // User wants schedule at 22:00 Berlin time
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2024-01-15T12:00:00Z"),
            userZone = "Europe/Berlin",
        )

        // 22:00 Berlin (UTC+1) = 21:00 UTC
        // Now is 12:00 UTC = 13:00 Berlin, so ETA to 22:00 Berlin (21:00 UTC) = 9 hours
        schedule.calcExecutionEta(
            now = Instant.parse("2024-01-15T12:00:00Z"),
            reschedule = false,
        ) shouldBe Duration.ofHours(9)
    }

    @Test fun `stored timezone differs from explicit zone parameter`() {
        // Schedule stored with Europe/Berlin, but we pass UTC as explicit parameter
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2024-01-15T12:00:00Z"),
            userZone = "Europe/Berlin",
        )

        // When explicitly passing UTC, 22:00 UTC is used (not Berlin)
        // Now is 12:00 UTC, ETA to 22:00 UTC = 10 hours
        schedule.calcExecutionEta(
            now = Instant.parse("2024-01-15T12:00:00Z"),
            reschedule = false,
            zone = ZoneId.of("UTC"),
        ) shouldBe Duration.ofHours(10)

        // Without explicit zone, stored Europe/Berlin is used
        // 22:00 Berlin (UTC+1) = 21:00 UTC, ETA = 9 hours
        schedule.calcExecutionEta(
            now = Instant.parse("2024-01-15T12:00:00Z"),
            reschedule = false,
        ) shouldBe Duration.ofHours(9)
    }

    @Test fun `backward compatibility - null userZone uses provided zone parameter`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T12:00:00Z"),
            userZone = null, // Legacy schedule without stored timezone
        )

        // Should work with explicitly provided zone
        val eta = schedule.calcExecutionEta(
            now = Instant.parse("2022-01-01T12:00:00Z"),
            reschedule = false,
            zone = ZoneId.of("UTC"),
        )

        eta shouldNotBe null
        eta shouldBe Duration.ofHours(10)
    }

    @Test fun `backward compatibility - null userZone defaults to system timezone`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T12:00:00Z"),
            userZone = null,
        )

        // Should not crash when using default parameter (system timezone)
        val eta = schedule.calcExecutionEta(
            now = Instant.parse("2022-01-01T12:00:00Z"),
            reschedule = false,
        )

        eta shouldNotBe null
    }

    @Test fun `invalid stored timezone falls back gracefully`() {
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2022-01-01T12:00:00Z"),
            userZone = "Invalid/Timezone",
        )

        // Should not crash, falls back to system default
        val eta = schedule.calcExecutionEta(
            now = Instant.parse("2022-01-01T12:00:00Z"),
            reschedule = false,
        )

        eta shouldNotBe null
    }

    // DST Tests - verify local time is preserved across daylight saving transitions

    @Test fun `DST spring forward - local time preserved for daily schedule`() {
        // Europe/Berlin DST: 2024-03-31 at 02:00 -> 03:00 (UTC+1 becomes UTC+2)
        // Schedule at 10:00 Berlin time, daily interval
        val schedule = Schedule(
            id = "id",
            hour = 10,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2024-03-30T08:00:00Z"), // March 30, before DST
            userZone = "Europe/Berlin",
        )

        // Before DST (March 30): 10:00 Berlin = 09:00 UTC
        // After DST (March 31): 10:00 Berlin = 08:00 UTC (1 hour earlier in UTC!)
        // If using Duration.plus(24h), March 31 would run at 09:00 UTC = 11:00 Berlin (WRONG)
        // With calendar arithmetic, it correctly runs at 08:00 UTC = 10:00 Berlin

        // Query from March 31 at 07:00 UTC (= 09:00 Berlin, after DST)
        val eta = schedule.calcExecutionEta(
            now = Instant.parse("2024-03-31T07:00:00Z"),
            reschedule = false,
        )

        // Should be 1 hour to reach 08:00 UTC (= 10:00 Berlin)
        eta shouldBe Duration.ofHours(1)
    }

    @Test fun `DST fall back - local time preserved for daily schedule`() {
        // Europe/Berlin DST: 2024-10-27 at 03:00 -> 02:00 (UTC+2 becomes UTC+1)
        // Schedule at 10:00 Berlin time, daily interval
        val schedule = Schedule(
            id = "id",
            hour = 10,
            minute = 0,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.parse("2024-10-26T07:00:00Z"), // Oct 26, before fall back
            userZone = "Europe/Berlin",
        )

        // Before fall back (Oct 26): 10:00 Berlin = 08:00 UTC
        // After fall back (Oct 27): 10:00 Berlin = 09:00 UTC (1 hour later in UTC!)
        // If using Duration.plus(24h), Oct 27 would run at 08:00 UTC = 09:00 Berlin (WRONG)
        // With calendar arithmetic, it correctly runs at 09:00 UTC = 10:00 Berlin

        // Query from Oct 27 at 08:00 UTC (= 09:00 Berlin, after fall back)
        val eta = schedule.calcExecutionEta(
            now = Instant.parse("2024-10-27T08:00:00Z"),
            reschedule = false,
        )

        // Should be 1 hour to reach 09:00 UTC (= 10:00 Berlin)
        eta shouldBe Duration.ofHours(1)
    }

    @Test fun `DST spring forward - local time preserved for multi-day schedule`() {
        // Schedule at 10:00 Berlin, 3-day interval, crossing DST
        val schedule = Schedule(
            id = "id",
            hour = 10,
            minute = 0,
            repeatInterval = Duration.ofDays(3),
            scheduledAt = Instant.parse("2024-03-28T08:00:00Z"), // March 28
            userZone = "Europe/Berlin",
        )

        // March 28 (before DST): 10:00 Berlin = 09:00 UTC
        // March 31 (after DST): 10:00 Berlin = 08:00 UTC
        // 3 days later should still be at 10:00 Berlin local time

        // Query from March 31 at 07:00 UTC (after DST transition)
        val eta = schedule.calcExecutionEta(
            now = Instant.parse("2024-03-31T07:00:00Z"),
            reschedule = false,
        )

        // Should be 1 hour to reach 08:00 UTC (= 10:00 Berlin)
        eta shouldBe Duration.ofHours(1)
    }

    @Test fun `DST - multiple weeks crossing transition`() {
        // Schedule at 22:00 Berlin, weekly (7-day) interval
        val schedule = Schedule(
            id = "id",
            hour = 22,
            minute = 0,
            repeatInterval = Duration.ofDays(7),
            scheduledAt = Instant.parse("2024-03-24T20:00:00Z"), // March 24 (Sunday before DST)
            userZone = "Europe/Berlin",
        )

        // March 24 (before DST): 22:00 Berlin = 21:00 UTC
        // March 31 (after DST): 22:00 Berlin = 20:00 UTC

        // Query from March 31 at 19:00 UTC (after DST)
        val eta = schedule.calcExecutionEta(
            now = Instant.parse("2024-03-31T19:00:00Z"),
            reschedule = false,
        )

        // Should be 1 hour to reach 20:00 UTC (= 22:00 Berlin)
        eta shouldBe Duration.ofHours(1)
    }
}