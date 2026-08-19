package eu.darken.sdmse.stats.core.forecast

import eu.darken.sdmse.stats.core.LowStorage
import eu.darken.sdmse.stats.core.SpaceTracker
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import kotlin.math.absoluteValue

/**
 * Estimates how long a storage lasts before it hits the low-space floor.
 *
 * The rate comes from [history], everything else from the LIVE reading: routine sampling is
 * six-hourly, so the newest persisted row can be hours stale and a download that already crossed
 * the floor would still be reported as [StorageForecast.Filling].
 */
object StorageForecaster {

    const val MIN_OBSERVED_DAYS = 5
    const val MAX_GAP_DAYS = 2L
    const val MIN_RATE_DIVISOR = 400
    const val MAX_SPREAD_RATIO = 2
    const val DISPLAY_HORIZON_DAYS = 120L
    const val URGENT_DAYS = 14L
    const val URGENT_FREE_PERCENT = 20
    const val URGENT_FREE_MAX_BYTES = 25L * 1024 * 1024 * 1024

    /**
     * [lowStorageThresholdBytes] is the resolved low-storage floor, see [LowStorage.resolveThreshold].
     * Deliberately without a default: the user can configure this, so every call site has to state
     * which value it is forecasting against.
     */
    fun forecast(
        history: List<SpaceSnapshotEntity>,
        current: SpaceTracker.StorageSnapshot,
        lowStorageThresholdBytes: Long,
    ): StorageForecast {
        val capacity = current.spaceCapacity
        if (capacity <= 0L) return StorageForecast.InsufficientData

        val floor = lowStorageThresholdBytes
        if (LowStorage.isLow(current.spaceFree, floor)) return StorageForecast.BelowFloor

        val trend = StorageTrendCalculator.dailyTrend(history) ?: return StorageForecast.InsufficientData
        if (trend.observedDays < MIN_OBSERVED_DAYS) return StorageForecast.InsufficientData
        if (trend.usableRateCount < MIN_OBSERVED_DAYS - 1) return StorageForecast.InsufficientData
        if (trend.maxGapDays > MAX_GAP_DAYS) return StorageForecast.InsufficientData

        val bytesPerDay = trend.bytesPerDay
        // The spread test is relative to the rate, so near a zero median it would flag single-byte
        // jitter. The noise floor gives it an absolute minimum, which keeps a device that swings
        // gigabytes a day around a zero median erratic instead of stable.
        val noiseFloor = capacity / MIN_RATE_DIVISOR
        val spreadThreshold = maxOf(MAX_SPREAD_RATIO * bytesPerDay.absoluteValue, noiseFloor)
        if (trend.spreadBytes > spreadThreshold) return StorageForecast.Erratic
        if (bytesPerDay <= 0L || bytesPerDay < noiseFloor) return StorageForecast.Stable

        val headroom = current.spaceFree - floor
        // Ceiling division: floor division reports "About 0 days" whenever the headroom is under a
        // day's worth of growth, and biases every other estimate early.
        val daysUntilFloor = (headroom - 1) / bytesPerDay + 1
        if (daysUntilFloor > DISPLAY_HORIZON_DAYS) return StorageForecast.Stable

        val urgentFreeCeiling = minOf(capacity * URGENT_FREE_PERCENT / 100, URGENT_FREE_MAX_BYTES)
        return StorageForecast.Filling(
            daysUntilFloor = daysUntilFloor,
            bytesPerDay = bytesPerDay,
            isUrgent = daysUntilFloor <= URGENT_DAYS && current.spaceFree <= urgentFreeCeiling,
        )
    }
}
