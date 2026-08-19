package eu.darken.sdmse.stats.core

/**
 * The single definition of "this storage is running out of space", shared by the dashboard
 * forecast, the home-screen widget and the settings screen that configures it.
 */
object LowStorage {

    const val AUTO_PERCENT = 5
    const val AUTO_MAX_BYTES = 2L * 1024 * 1024 * 1024

    /**
     * [customThresholdBytes] null means automatic: the historical rule, the smaller of 5% of
     * capacity and 2 GiB. A non-null value is used exactly — no capacity clamp.
     */
    fun resolveThreshold(capacityBytes: Long, customThresholdBytes: Long?): Long = when {
        customThresholdBytes != null -> customThresholdBytes.coerceAtLeast(0L)
        else -> minOf(capacityBytes * AUTO_PERCENT / 100, AUTO_MAX_BYTES)
    }

    /** Inclusive: exactly at the threshold counts as low. */
    fun isLow(spaceFreeBytes: Long, thresholdBytes: Long): Boolean = spaceFreeBytes <= thresholdBytes
}
