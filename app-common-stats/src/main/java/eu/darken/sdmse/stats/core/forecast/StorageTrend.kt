package eu.darken.sdmse.stats.core.forecast

/**
 * A robust per-day storage growth estimate derived from a single storage's snapshot history.
 *
 * [bytesPerDay] is a median of per-day rates, not a first-to-last delta, so a cleanup inside the
 * window (which forces a snapshot with sharply more free space) can no longer flip the trend.
 */
data class DailyTrend(
    /** Positive means the storage is filling up. */
    val bytesPerDay: Long,
    /** Number of distinct calendar day buckets that contributed a reading. */
    val observedDays: Int,
    /** Number of consecutive bucket pairs that actually yielded a rate, i.e. the sample size. */
    val usableRateCount: Int,
    /** Calendar days between the first and the last bucket. */
    val elapsedDays: Long,
    /** Largest hole between two consecutive buckets, in days. */
    val maxGapDays: Long,
    /** Median absolute deviation of the per-day rates. */
    val spreadBytes: Long,
)

sealed interface StorageForecast {

    data class Filling(
        val daysUntilFloor: Long,
        val bytesPerDay: Long,
        val isUrgent: Boolean,
    ) : StorageForecast

    data object Stable : StorageForecast

    data object Erratic : StorageForecast

    data object InsufficientData : StorageForecast

    data object BelowFloor : StorageForecast
}
