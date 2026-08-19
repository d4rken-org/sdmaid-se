package eu.darken.sdmse.stats.core.forecast

import eu.darken.sdmse.stats.core.SpaceTracker
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class StorageForecasterTest : BaseTest() {

    private val base = LocalDate.parse("2026-06-01")
    private val capacity = 100_000_000_000L
    private val floor = 2_147_483_648L
    private val minRate = capacity / StorageForecaster.MIN_RATE_DIVISOR

    private fun snap(
        day: Long,
        used: Long,
        capacity: Long = this.capacity,
        hour: Long = 12L,
    ) = SpaceSnapshotEntity(
        storageId = "a",
        recordedAt = base.plusDays(day).atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(hour)),
        spaceFree = capacity - used,
        spaceCapacity = capacity,
    )

    private fun history(
        ratePerDay: Long,
        days: Int = 7,
        startUsed: Long = 10_000_000_000L,
    ): List<SpaceSnapshotEntity> = (0 until days).map { snap(day = it.toLong(), used = startUsed + ratePerDay * it) }

    private fun current(free: Long, capacity: Long = this.capacity) = SpaceTracker.StorageSnapshot(
        storageId = "a",
        spaceFree = free,
        spaceCapacity = capacity,
    )

    @Test
    fun `already below the floor short-circuits everything`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate),
            current = current(free = 1_000_000_000L),
        ) shouldBe StorageForecast.BelowFloor
    }

    @Test
    fun `exactly at the floor counts as below it`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate),
            current = current(free = floor),
        ) shouldBe StorageForecast.BelowFloor
    }

    @Test
    fun `a zero capacity reading is insufficient data`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate),
            current = current(free = 20_000_000_000L, capacity = 0L),
        ) shouldBe StorageForecast.InsufficientData
    }

    @Test
    fun `a history without a daily trend is insufficient data`() {
        val sameDay = listOf(
            snap(day = 0, used = 10_000_000_000L, hour = 1),
            snap(day = 0, used = 11_000_000_000L, hour = 5),
        )
        StorageForecaster.forecast(
            history = sameDay,
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.InsufficientData
    }

    @Test
    fun `fewer observed days than the minimum is insufficient data`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate, days = StorageForecaster.MIN_OBSERVED_DAYS - 1),
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.InsufficientData
    }

    @Test
    fun `exactly the minimum observed days is enough`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate, days = StorageForecaster.MIN_OBSERVED_DAYS),
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.Filling(daysUntilFloor = 72, bytesPerDay = minRate, isUrgent = false)
    }

    @Test
    fun `a gap larger than the maximum is insufficient data`() {
        val gapped = listOf(0L, 1L, 2L, 3L, 7L).map { snap(day = it, used = 10_000_000_000L + minRate * it) }
        StorageForecaster.forecast(
            history = gapped,
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.InsufficientData
    }

    @Test
    fun `a gap at the maximum is still usable`() {
        val gapped = listOf(0L, 1L, 2L, 3L, 5L).map { snap(day = it, used = 10_000_000_000L + minRate * it) }
        StorageForecaster.forecast(
            history = gapped,
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.Filling(daysUntilFloor = 72, bytesPerDay = minRate, isUrgent = false)
    }

    @Test
    fun `a series that swings both ways is erratic`() {
        val erratic = listOf(
            10_000_000_000L,
            10_100_000_000L,
            9_800_000_000L,
            10_300_000_000L,
            10_100_000_000L,
        ).mapIndexed { index, used -> snap(day = index.toLong(), used = used) }

        StorageForecaster.forecast(
            history = erratic,
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.Erratic
    }

    @Test
    fun `a flat history is stable`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = 0L),
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.Stable
    }

    @Test
    fun `a shrinking history is stable`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = -minRate, startUsed = 50_000_000_000L),
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.Stable
    }

    @Test
    fun `a rate just under the noise threshold is stable`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate - 1),
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.Stable
    }

    @Test
    fun `a rate exactly at the noise threshold is a forecast`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate),
            current = current(free = 20_000_000_000L),
        ) shouldBe StorageForecast.Filling(daysUntilFloor = 72, bytesPerDay = minRate, isUrgent = false)
    }

    @Test
    fun `headroom below a single day of growth is one day, never zero`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = 1_000_000_000L),
            current = current(free = floor + 1),
        ) shouldBe StorageForecast.Filling(daysUntilFloor = 1, bytesPerDay = 1_000_000_000L, isUrgent = true)
    }

    @Test
    fun `a partial day rounds up`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = 1_000_000_000L),
            current = current(free = floor + 2_500_000_000L),
        ) shouldBe StorageForecast.Filling(daysUntilFloor = 3, bytesPerDay = 1_000_000_000L, isUrgent = true)
    }

    @Test
    fun `an exact day count is not rounded up further`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = 1_000_000_000L),
            current = current(free = floor + 3_000_000_000L),
        ) shouldBe StorageForecast.Filling(daysUntilFloor = 3, bytesPerDay = 1_000_000_000L, isUrgent = true)
    }

    @Test
    fun `exactly at the display horizon still forecasts`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate),
            current = current(free = floor + minRate * StorageForecaster.DISPLAY_HORIZON_DAYS),
        ) shouldBe StorageForecast.Filling(
            daysUntilFloor = StorageForecaster.DISPLAY_HORIZON_DAYS,
            bytesPerDay = minRate,
            isUrgent = false,
        )
    }

    @Test
    fun `one byte past the display horizon is stable`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate),
            current = current(free = floor + minRate * StorageForecaster.DISPLAY_HORIZON_DAYS + 1),
        ) shouldBe StorageForecast.Stable
    }

    @Test
    fun `exactly at the urgent day count is urgent`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate),
            current = current(free = floor + minRate * StorageForecaster.URGENT_DAYS),
        ) shouldBe StorageForecast.Filling(
            daysUntilFloor = StorageForecaster.URGENT_DAYS,
            bytesPerDay = minRate,
            isUrgent = true,
        )
    }

    @Test
    fun `one byte past the urgent day count is not urgent`() {
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate),
            current = current(free = floor + minRate * StorageForecaster.URGENT_DAYS + 1),
        ) shouldBe StorageForecast.Filling(
            daysUntilFloor = StorageForecaster.URGENT_DAYS + 1,
            bytesPerDay = minRate,
            isUrgent = false,
        )
    }

    @Test
    fun `a roomy device is not urgent even when the floor is days away`() {
        // 25 GB free on a 100 GB device is above the urgency ceiling, so a 5 day estimate still
        // renders calm: there is plenty of room to react.
        StorageForecaster.forecast(
            history = history(ratePerDay = 5_000_000_000L),
            current = current(free = 25_000_000_000L),
        ) shouldBe StorageForecast.Filling(daysUntilFloor = 5, bytesPerDay = 5_000_000_000L, isUrgent = false)
    }

    @Test
    fun `the live reading wins over a stale history`() {
        // Sampling is six-hourly: a download since the newest persisted row can already have
        // crossed the floor while the history still describes a comfortable fill rate.
        StorageForecaster.forecast(
            history = history(ratePerDay = minRate),
            current = current(free = floor - 1),
        ) shouldBe StorageForecast.BelowFloor
    }
}
