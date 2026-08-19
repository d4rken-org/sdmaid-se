package eu.darken.sdmse.stats.core.forecast

import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

/**
 * Turns raw space snapshots into a per-day growth rate.
 *
 * A snapshot is forced after every task, so a first-to-last delta over the window reports a net
 * free-space gain whenever the user cleaned up inside it. Bucketing per day and taking the median
 * of the per-day rates survives that.
 */
object StorageTrendCalculator {

    /**
     * @param snapshots snapshots of a SINGLE storage; callers with mixed storages must group first.
     */
    fun dailyTrend(snapshots: List<SpaceSnapshotEntity>, zone: ZoneId = ZoneOffset.UTC): DailyTrend? {
        val sorted = validSorted(snapshots)
        if (sorted.size < 2) return null

        val buckets = sorted
            .groupBy { it.recordedAt.atZone(zone).toLocalDate() }
            .toSortedMap()
            .map { (day, entries) ->
                val reading = entries.last()
                Bucket(
                    day = day,
                    used = reading.spaceCapacity - reading.spaceFree,
                    capacity = reading.spaceCapacity,
                )
            }
        if (buckets.size < 2) return null

        val rates = mutableListOf<Long>()
        var maxGapDays = 0L
        buckets.zipWithNext { earlier, later ->
            val gap = ChronoUnit.DAYS.between(earlier.day, later.day)
            if (gap > maxGapDays) maxGapDays = gap
            // A capacity flip is a StorageStatsManager/File-fallback disagreement, not user growth.
            if (earlier.capacity == later.capacity) rates.add((later.used - earlier.used) / gap)
        }
        if (rates.isEmpty()) return null

        val bytesPerDay = median(rates)

        return DailyTrend(
            bytesPerDay = bytesPerDay,
            observedDays = buckets.size,
            usableRateCount = rates.size,
            elapsedDays = ChronoUnit.DAYS.between(buckets.first().day, buckets.last().day),
            maxGapDays = maxGapDays,
            spreadBytes = median(rates.map { (it - bytesPerDay).absoluteValue }),
        )
    }

    /**
     * Total used-space change across the window, in bytes. Positive means the storage filled up.
     *
     * Falls back to the raw first-to-last delta for histories too short to bucket into days, so
     * sub-day windows keep rendering a value instead of a blank.
     */
    fun windowTotal(snapshots: List<SpaceSnapshotEntity>): Long? {
        dailyTrend(snapshots)?.let { return it.bytesPerDay * it.elapsedDays }

        val sorted = validSorted(snapshots)
        if (sorted.size < 2) return null
        return sorted.last().usedSpace - sorted.first().usedSpace
    }

    private fun validSorted(snapshots: List<SpaceSnapshotEntity>): List<SpaceSnapshotEntity> = snapshots
        .filter { it.spaceCapacity > 0L && it.spaceFree >= 0L && it.spaceFree <= it.spaceCapacity }
        .sortedWith(compareBy({ it.recordedAt }, { it.id }))

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]).floorDiv(2L)
        }
    }

    private val SpaceSnapshotEntity.usedSpace: Long
        get() = spaceCapacity - spaceFree

    private data class Bucket(
        val day: LocalDate,
        val used: Long,
        val capacity: Long,
    )
}
