package eu.darken.sdmse.common.storage

import kotlin.math.abs

/**
 * Reconciles capacity/free-space readings from [StorageStatsManager2] against the filesystem
 * (StatFs/File) reading for the same volume.
 *
 * `StorageStatsManager.getTotalBytes(...)` is unreliable on some vendor ROMs. We've seen it:
 * - return ~2x the real capacity for the PRIMARY volume on realme/ColorOS Android 15 (free stays
 *   correct, so used = capacity - free gets doubled too).
 * - return a doubled/garbage total for FAT-synthesised-UUID SD cards
 *   (https://github.com/d4rken-org/sdmaid-se/issues/2389).
 * - express the advertised capacity in binary units, e.g. 137438953472 (128 * 2^30) on a phone sold
 *   as 128 GB, where AOSP would report 128000000000.
 *
 * When the framework value is implausible we fall back to the filesystem reading, which matches
 * what other tools (e.g. DiskInfo) and the actual partition report.
 */
object StorageSpaceReconcile {

    private const val GIB = 1L shl 30
    private const val GB = 1_000_000_000L
    private const val TIB = 1L shl 40
    private const val TB = 1_000_000_000_000L

    data class Result(
        val total: Long,
        val free: Long,
        /** True when the filesystem reading was preferred over the StorageStatsManager values. */
        val usedFileFallback: Boolean,
        /** True when a binary-expressed StorageStatsManager total was converted to decimal units. */
        val normalizedStatsTotal: Boolean = false,
    )

    /**
     * PRIMARY volume policy.
     *
     * `getTotalBytes(UUID_DEFAULT)` is legitimately a little larger than `StatFs` on the data
     * partition (AOSP rounds it up to the advertised "marketing" size), so a symmetric mismatch
     * rule like the secondary one would regress normal devices. We therefore only override on
     * *gross* over-inflation (>1.5x the filesystem total) AND only when the free-space readings
     * agree — the doubled-total bug leaves free correct, so free agreement is the signal that the
     * two APIs describe the same volume and the total is simply wrong.
     *
     * [normalizeStatsTotal] cannot influence that decision: the over-inflation rule reads the raw
     * [statsTotal]/[statsFree] parameters and never the normalized result.
     */
    fun reconcilePrimary(
        statsTotal: Long,
        statsFree: Long,
        fileTotal: Long,
        fileFree: Long,
    ): Result {
        val statsResult = normalizeStatsTotal(statsTotal, statsFree, fileTotal)

        // No usable filesystem cross-check.
        if (fileTotal <= 0L) return statsResult
        // Filesystem pair must itself be sane before we trust it.
        if (fileFree < 0L || fileFree > fileTotal) return statsResult

        // statsTotal > 1.5 * fileTotal, expressed without floating point.
        val grosslyInflated = statsTotal * 2 > fileTotal * 3
        // Free readings agree within ~5% of capacity -> same volume, only the total is wrong.
        val freeAgrees = abs(statsFree - fileFree) * 20 < fileTotal

        return if (grosslyInflated && freeAgrees) {
            Result(fileTotal, fileFree, usedFileFallback = true)
        } else {
            statsResult
        }
    }

    /**
     * `getTotalBytes(...)` is specified to return the capacity the device is *sold* as, which AOSP
     * derives via `FileUtils.roundStorageSize` and is therefore always `{1,2,4,...,512} * 1000^n`.
     * Some ROMs round to `1024^n` instead, so a 128 GB phone reports 137438953472 and the card shows
     * "137 GB". An exact multiple of 2^30 that isn't an exact multiple of 10^9 can only come from
     * such a ROM, and the marketing value is the same mantissa in decimal units.
     *
     * The mantissa only names its own tier, so each binary unit is paired with its decimal
     * counterpart: 2^40 with 10^12, 2^30 with 10^9. 2^40 is tested first because every multiple of
     * it is also a multiple of 2^30, which would turn a 1 TB device's 1 TiB reading into 1024 GB.
     * The 10^9 exclusion covers both tiers, as every multiple of 10^12 is also a multiple of 10^9.
     *
     * Two sanity guards keep us from inventing a capacity that contradicts the rest of the reading:
     * the conversion shrinks the total by ~7-9% while free space is untouched, so a candidate below
     * [statsFree] would make derived used space negative, and a candidate below [fileTotal] would
     * claim a retail capacity smaller than the measured filesystem. The latter also catches ROMs
     * that return a real filesystem measurement here: those land near [fileTotal], so their
     * candidate falls to ~0.91-0.93x of it and is rejected.
     */
    private fun normalizeStatsTotal(
        statsTotal: Long,
        statsFree: Long,
        fileTotal: Long,
    ): Result {
        val raw = Result(statsTotal, statsFree, usedFileFallback = false)
        if (statsTotal <= 0L || statsTotal % GB == 0L) return raw

        val candidate = when {
            statsTotal % TIB == 0L -> (statsTotal / TIB) * TB
            statsTotal % GIB == 0L -> (statsTotal / GIB) * GB
            else -> return raw
        }
        if (candidate < statsFree) return raw
        if (fileTotal > 0L && candidate < fileTotal) return raw

        return Result(candidate, statsFree, usedFileFallback = false, normalizedStatsTotal = true)
    }

    /**
     * SECONDARY volume policy (unchanged from #2389).
     *
     * For FAT synthesised UUIDs, StorageStatsManager is unreliable on some devices. If StatFs
     * disagrees with the API by more than 10%, trust the filesystem.
     */
    fun reconcileSecondary(
        statsTotal: Long,
        statsFree: Long,
        fileTotal: Long,
        fileFree: Long,
        isFatUuid: Boolean,
    ): Result {
        val mismatches = isFatUuid && fileTotal > 0L && abs(statsTotal - fileTotal) * 10 > fileTotal
        return if (mismatches) {
            Result(fileTotal, fileFree, usedFileFallback = true)
        } else {
            Result(statsTotal, statsFree, usedFileFallback = false)
        }
    }
}
