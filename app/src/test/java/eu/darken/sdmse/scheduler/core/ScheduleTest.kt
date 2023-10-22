package eu.darken.sdmse.scheduler.core

import io.kotest.matchers.shouldBe
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
            userZone = ZoneId.of("UTC")
        ) shouldBe Instant.parse("2022-01-01T22:00:00Z")
        schedule.calcFirstExecutionAt(
            userZone = ZoneId.of("GMT+1")
        ) shouldBe Instant.parse("2022-01-01T21:00:00Z")
        schedule.calcFirstExecutionAt(
            userZone = ZoneId.of("GMT-1")
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
            userZone = ZoneId.of("UTC"),
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
            userZone = ZoneId.of("GMT+1"),
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
            userZone = ZoneId.of("UTC"),
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
            userZone = ZoneId.of("GMT-1"),
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
            userZone = ZoneId.of("UTC"),
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
            userZone = ZoneId.of("UTC"),
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
            userZone = ZoneId.of("UTC"),
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
            userZone = ZoneId.of("UTC"),
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
            userZone = ZoneId.of("GMT-1"),
            reschedule = true,
            now = Instant.parse("2022-01-02T11:55:00Z")
        ) shouldBe Duration.ofHours(25).plusMinutes(5)
    }
}