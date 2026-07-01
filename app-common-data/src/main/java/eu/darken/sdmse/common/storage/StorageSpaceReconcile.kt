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
 *
 * When the framework value is implausible we fall back to the filesystem reading, which matches
 * what other tools (e.g. DiskInfo) and the actual partition report.
 */
object StorageSpaceReconcile {

    data class Result(
        val total: Long,
        val free: Long,
        /** True when the filesystem reading was preferred over the StorageStatsManager values. */
        val usedFileFallback: Boolean,
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
     */
    fun reconcilePrimary(
        statsTotal: Long,
        statsFree: Long,
        fileTotal: Long,
        fileFree: Long,
    ): Result {
        val statsResult = Result(statsTotal, statsFree, usedFileFallback = false)

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
