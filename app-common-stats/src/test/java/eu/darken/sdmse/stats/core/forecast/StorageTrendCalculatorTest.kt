package eu.darken.sdmse.stats.core.forecast

import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class StorageTrendCalculatorTest : BaseTest() {

    private val base = LocalDate.parse("2026-06-01")
    private val capacity = 1_000_000L

    private fun raw(
        day: Long,
        free: Long,
        capacity: Long,
        id: Long = 0L,
        hour: Long = 12L,
    ) = SpaceSnapshotEntity(
        id = id,
        storageId = "a",
        recordedAt = base.plusDays(day).atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(hour)),
        spaceFree = free,
        spaceCapacity = capacity,
    )

    private fun snap(
        day: Long,
        used: Long,
        capacity: Long = this.capacity,
        id: Long = 0L,
        hour: Long = 12L,
    ) = raw(day = day, free = capacity - used, capacity = capacity, id = id, hour = hour)

    private fun series(days: LongRange, used: (Long) -> Long) = days.map { snap(day = it, used = used(it)) }

    @Test
    fun `a steady fill yields its own rate`() {
        val trend = StorageTrendCalculator.dailyTrend(series(0L..6L) { 20_000 + 100 * it }).shouldNotBeNull()
        trend.bytesPerDay shouldBe 100L
        trend.observedDays shouldBe 7
        trend.elapsedDays shouldBe 6L
        trend.maxGapDays shouldBe 1L
        trend.spreadBytes shouldBe 0L
        StorageTrendCalculator.windowTotal(series(0L..6L) { 20_000 + 100 * it }) shouldBe 600L
    }

    @Test
    fun `a mid-window cleanup does not drag the rate down`() {
        val snapshots = series(0L..6L) { 20_000 + 100 * it - if (it >= 3) 2_000 else 0 }
        StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull().bytesPerDay shouldBe 100L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 600L
    }

    @Test
    fun `a cleanup on the last day cannot flip a filling window negative`() {
        // The task coordinator forces a snapshot after every task, so the newest point of a window
        // that ends in a cleanup shows sharply more free space. First-to-last would report -50.
        val snapshots = series(0L..6L) { if (it == 6L) 19_950 else 20_000 + 100 * it }
        rawFirstToLast(snapshots) shouldBe -50L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 600L
    }

    @Test
    fun `a single large install does not inflate the rate`() {
        val snapshots = series(0L..6L) { 20_000 + 100 * it + if (it >= 3) 47_000 else 0 }
        StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull().bytesPerDay shouldBe 100L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 600L
    }

    @Test
    fun `input order does not matter`() {
        val snapshots = series(0L..6L) { 20_000 + 100 * it }
        StorageTrendCalculator.windowTotal(snapshots.shuffled()) shouldBe 600L
    }

    @Test
    fun `equal timestamps are broken by id, highest wins the bucket`() {
        val snapshots = listOf(
            snap(day = 0, used = 100, id = 2),
            snap(day = 0, used = 300, id = 7),
            snap(day = 1, used = 400, id = 9),
        )
        StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull().bytesPerDay shouldBe 100L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 100L
    }

    @Test
    fun `a missing intermediate day still totals the true span`() {
        val snapshots = listOf(
            snap(day = 0, used = 100),
            snap(day = 2, used = 300),
        )
        val trend = StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull()
        trend.bytesPerDay shouldBe 100L
        trend.observedDays shouldBe 2
        trend.elapsedDays shouldBe 2L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 200L
    }

    @Test
    fun `gaps are reported as the largest hole between buckets`() {
        val oneDay = listOf(snap(day = 0, used = 100), snap(day = 1, used = 200))
        StorageTrendCalculator.dailyTrend(oneDay).shouldNotBeNull().maxGapDays shouldBe 1L

        val twoDays = listOf(
            snap(day = 0, used = 100),
            snap(day = 1, used = 200),
            snap(day = 3, used = 400),
        )
        val trend = StorageTrendCalculator.dailyTrend(twoDays).shouldNotBeNull()
        trend.maxGapDays shouldBe 2L
        trend.observedDays shouldBe 3
        trend.elapsedDays shouldBe 3L
    }

    @Test
    fun `same-day snapshots have no daily trend but keep a raw window total`() {
        val snapshots = listOf(
            snap(day = 0, used = 100, hour = 1),
            snap(day = 0, used = 200, hour = 2),
        )
        StorageTrendCalculator.dailyTrend(snapshots).shouldBeNull()
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 100L
    }

    @Test
    fun `a single snapshot has neither trend nor total`() {
        val snapshots = listOf(snap(day = 0, used = 100))
        StorageTrendCalculator.dailyTrend(snapshots).shouldBeNull()
        StorageTrendCalculator.windowTotal(snapshots).shouldBeNull()
    }

    @Test
    fun `an empty history has neither trend nor total`() {
        StorageTrendCalculator.dailyTrend(emptyList()).shouldBeNull()
        StorageTrendCalculator.windowTotal(emptyList()).shouldBeNull()
    }

    @Test
    fun `a capacity change skips the pair it spans`() {
        val snapshots = listOf(
            snap(day = 0, used = 100, capacity = 1000),
            snap(day = 1, used = 200, capacity = 1000),
            snap(day = 2, used = 1200, capacity = 2000),
            snap(day = 3, used = 1300, capacity = 2000),
        )
        val trend = StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull()
        trend.bytesPerDay shouldBe 100L
        trend.spreadBytes shouldBe 0L
        trend.elapsedDays shouldBe 3L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 300L
    }

    @Test
    fun `alternating capacities leave only the surviving pairs as usable rates`() {
        // A ROM whose capacity flips between the StorageStatsManager and the File fallback still
        // fills every day bucket, but almost every pair is unusable.
        val snapshots = listOf(
            snap(day = 0, used = 100, capacity = 1000),
            snap(day = 1, used = 1100, capacity = 2000),
            snap(day = 2, used = 200, capacity = 1000),
            snap(day = 3, used = 1200, capacity = 2000),
            snap(day = 4, used = 1300, capacity = 2000),
        )
        val trend = StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull()
        trend.observedDays shouldBe 5
        trend.usableRateCount shouldBe 1
        trend.bytesPerDay shouldBe 100L
        trend.spreadBytes shouldBe 0L
    }

    @Test
    fun `a capacity change on every pair leaves no rate at all`() {
        val snapshots = listOf(
            snap(day = 0, used = 100, capacity = 1000),
            snap(day = 1, used = 200, capacity = 2000),
            snap(day = 2, used = 300, capacity = 3000),
        )
        StorageTrendCalculator.dailyTrend(snapshots).shouldBeNull()
    }

    @Test
    fun `invalid rows are dropped before anything else`() {
        val snapshots = listOf(
            snap(day = 0, used = 100),
            raw(day = 1, free = -5, capacity = capacity),
            raw(day = 2, free = capacity + 1, capacity = capacity),
            raw(day = 3, free = 0, capacity = 0),
            snap(day = 4, used = 500),
        )
        val trend = StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull()
        trend.observedDays shouldBe 2
        trend.elapsedDays shouldBe 4L
        trend.maxGapDays shouldBe 4L
        trend.bytesPerDay shouldBe 100L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 400L
    }

    @Test
    fun `a cleanup outlier at the minimum length leaves the spread at zero`() {
        // Four rates of [1, 1, 1, -100]: the interquartile range would call this erratic, the
        // median absolute deviation correctly reports no spread at all.
        val snapshots = listOf(
            snap(day = 0, used = 1000),
            snap(day = 1, used = 1001),
            snap(day = 2, used = 1002),
            snap(day = 3, used = 1003),
            snap(day = 4, used = 903),
        )
        val trend = StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull()
        trend.observedDays shouldBe 5
        trend.bytesPerDay shouldBe 1L
        trend.spreadBytes shouldBe 0L
    }

    @Test
    fun `an install outlier at the minimum length leaves the spread at zero`() {
        val snapshots = listOf(
            snap(day = 0, used = 1000),
            snap(day = 1, used = 1001),
            snap(day = 2, used = 1002),
            snap(day = 3, used = 1003),
            snap(day = 4, used = 1103),
        )
        val trend = StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull()
        trend.bytesPerDay shouldBe 1L
        trend.spreadBytes shouldBe 0L
    }

    @Test
    fun `a genuinely erratic series has a spread larger than its rate`() {
        val snapshots = listOf(
            snap(day = 0, used = 1000),
            snap(day = 1, used = 1100),
            snap(day = 2, used = 800),
            snap(day = 3, used = 1300),
            snap(day = 4, used = 1100),
        )
        val trend = StorageTrendCalculator.dailyTrend(snapshots).shouldNotBeNull()
        trend.bytesPerDay shouldBe -50L
        trend.spreadBytes shouldBe 200L
    }

    @Test
    fun `a 7 day window with a cleanup totals the honest fill`() {
        val snapshots = series(0L..6L) { 20_000 + 100 * it - if (it >= 5) 3_000 else 0 }
        rawFirstToLast(snapshots) shouldBe -2_400L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 600L
    }

    @Test
    fun `a 30 day window with a cleanup totals the honest fill`() {
        val snapshots = series(0L..29L) { 20_000 + 100 * it - if (it >= 20) 5_000 else 0 }
        rawFirstToLast(snapshots) shouldBe -2_100L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 2_900L
    }

    @Test
    fun `a 90 day window with a cleanup totals the honest fill`() {
        val snapshots = series(0L..89L) { 20_000 + 100 * it - if (it >= 60) 8_000 else 0 }
        rawFirstToLast(snapshots) shouldBe 900L
        StorageTrendCalculator.windowTotal(snapshots) shouldBe 8_900L
    }

    private fun rawFirstToLast(snapshots: List<SpaceSnapshotEntity>): Long {
        val sorted = snapshots.sortedBy { it.recordedAt }
        return (sorted.last().spaceCapacity - sorted.last().spaceFree) -
            (sorted.first().spaceCapacity - sorted.first().spaceFree)
    }
}
