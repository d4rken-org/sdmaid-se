package eu.darken.sdmse.common.storage

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StorageSpaceReconcileTest : BaseTest() {

    // ~1TB device reported as 2TB, free correct. Reproduces realme/ColorOS A15 report.
    private val TB = 1_000_000_000_000L
    private val GB = 1_000_000_000L
    private val GIB = 1L shl 30
    private val TIB = 1L shl 40

    // --- reconcilePrimary ---

    @Test
    fun `primary grossly inflated total with agreeing free prefers File`() {
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 2 * TB,
            statsFree = 104 * GB,
            fileTotal = 1_010 * GB,
            fileFree = 105 * GB,
        )
        result.total shouldBe 1_010 * GB
        result.free shouldBe 105 * GB
        result.usedFileFallback shouldBe true
    }

    @Test
    fun `primary normal marketing round-up keeps stats`() {
        // getTotalBytes rounds up to advertised size; StatFs on data is slightly smaller. Not a bug.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 128 * GB,
            statsFree = 64 * GB,
            fileTotal = 115 * GB,
            fileFree = 60 * GB,
        )
        result.total shouldBe 128 * GB
        result.usedFileFallback shouldBe false
    }

    @Test
    fun `primary at exactly 1_5x keeps stats`() {
        // statsTotal * 2 > fileTotal * 3 is a strict >1.5x; exactly 1.5x must NOT override.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 150 * GB,
            statsFree = 50 * GB,
            fileTotal = 100 * GB,
            fileFree = 50 * GB,
        )
        result.total shouldBe 150 * GB
        result.usedFileFallback shouldBe false
    }

    @Test
    fun `primary just above 1_5x with agreeing free prefers File`() {
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 151 * GB,
            statsFree = 50 * GB,
            fileTotal = 100 * GB,
            fileFree = 50 * GB,
        )
        result.total shouldBe 100 * GB
        result.usedFileFallback shouldBe true
    }

    @Test
    fun `primary inflated total but divergent free keeps stats`() {
        // Free readings disagree -> the two APIs may describe different scopes; don't second-guess.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 2 * TB,
            statsFree = 500 * GB,
            fileTotal = 1_010 * GB,
            fileFree = 105 * GB,
        )
        result.total shouldBe 2 * TB
        result.usedFileFallback shouldBe false
    }

    @Test
    fun `primary with no filesystem total keeps stats`() {
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 2 * TB,
            statsFree = 104 * GB,
            fileTotal = 0L,
            fileFree = 0L,
        )
        result.total shouldBe 2 * TB
        result.usedFileFallback shouldBe false
    }

    @Test
    fun `primary with invalid filesystem pair keeps stats`() {
        // fileFree > fileTotal is nonsensical; never override to a bad pair.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 2 * TB,
            statsFree = 104 * GB,
            fileTotal = 1_010 * GB,
            fileFree = 2_000 * GB,
        )
        result.total shouldBe 2 * TB
        result.usedFileFallback shouldBe false
    }

    // --- reconcilePrimary: binary-rounded marketing capacity ---

    @Test
    fun `primary binary-rounded total converts to decimal units`() {
        // 128 GiB = 137438953472 on a phone sold as 128 GB.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 128 * GIB,
            statsFree = 60 * GB,
            fileTotal = 115 * GB,
            fileFree = 60 * GB,
        )
        result.total shouldBe 128 * GB
        result.free shouldBe 60 * GB
        result.usedFileFallback shouldBe false
        result.normalizedStatsTotal shouldBe true
    }

    @Test
    fun `primary binary-rounded terabyte total converts to decimal units`() {
        // 1 TiB = 1099511627776 on a device sold as 1 TB; the GiB tier would yield 1024 GB.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = TIB,
            statsFree = 400 * GB,
            fileTotal = 950 * GB,
            fileFree = 400 * GB,
        )
        result.total shouldBe TB
        result.free shouldBe 400 * GB
        result.usedFileFallback shouldBe false
        result.normalizedStatsTotal shouldBe true
    }

    @Test
    fun `primary binary-rounded total below the terabyte tier stays on the GiB tier`() {
        // 512 GiB is a multiple of 2^30 but not of 2^40, so it must convert to 512 GB.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 512 * GIB,
            statsFree = 200 * GB,
            fileTotal = 480 * GB,
            fileFree = 200 * GB,
        )
        result.total shouldBe 512 * GB
        result.usedFileFallback shouldBe false
        result.normalizedStatsTotal shouldBe true
    }

    @Test
    fun `primary AOSP-style decimal total is not converted`() {
        // Feeding the converted value back in must be a no-op.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 128 * GB,
            statsFree = 60 * GB,
            fileTotal = 115 * GB,
            fileFree = 60 * GB,
        )
        result.total shouldBe 128 * GB
        result.normalizedStatsTotal shouldBe false
    }

    @Test
    fun `primary non-round total is not converted`() {
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 137_438_953_000L,
            statsFree = 60 * GB,
            fileTotal = 115 * GB,
            fileFree = 60 * GB,
        )
        result.total shouldBe 137_438_953_000L
        result.normalizedStatsTotal shouldBe false
    }

    @Test
    fun `primary File fallback total is not converted`() {
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 2048 * GIB,
            statsFree = 104 * GB,
            fileTotal = 1024 * GIB,
            fileFree = 105 * GB,
        )
        result.total shouldBe 1024 * GIB
        result.usedFileFallback shouldBe true
        result.normalizedStatsTotal shouldBe false
    }

    @Test
    fun `primary over-inflated binary total still prefers File`() {
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 2048 * GIB,
            statsFree = 104 * GB,
            fileTotal = 1_010 * GB,
            fileFree = 105 * GB,
        )
        result.total shouldBe 1_010 * GB
        result.usedFileFallback shouldBe true
        result.normalizedStatsTotal shouldBe false
    }

    @Test
    fun `primary conversion is skipped when it would undercut free space`() {
        // 128 GB candidate < 130 GB free would make used space negative.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 128 * GIB,
            statsFree = 130 * GB,
            fileTotal = 120 * GB,
            fileFree = 118 * GB,
        )
        result.total shouldBe 128 * GIB
        result.normalizedStatsTotal shouldBe false
    }

    @Test
    fun `primary conversion is skipped when it would undercut the filesystem total`() {
        // A retail capacity below the measured filesystem size can't be right.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 128 * GIB,
            statsFree = 60 * GB,
            fileTotal = 130 * GB,
            fileFree = 60 * GB,
        )
        result.total shouldBe 128 * GIB
        result.normalizedStatsTotal shouldBe false
    }

    @Test
    fun `primary binary total converts without a filesystem cross-check`() {
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 128 * GIB,
            statsFree = 60 * GB,
            fileTotal = 0L,
            fileFree = 0L,
        )
        result.total shouldBe 128 * GB
        result.normalizedStatsTotal shouldBe true
    }

    @Test
    fun `primary over-inflation is judged on the raw total, not the converted one`() {
        // Raw 64 GiB is >1.5x the 45 GB filesystem total, the 64 GB candidate would not be.
        // Converting before the comparison would keep stats instead of falling back.
        val result = StorageSpaceReconcile.reconcilePrimary(
            statsTotal = 64 * GIB,
            statsFree = 20 * GB,
            fileTotal = 45 * GB,
            fileFree = 20 * GB,
        )
        result.total shouldBe 45 * GB
        result.free shouldBe 20 * GB
        result.usedFileFallback shouldBe true
        result.normalizedStatsTotal shouldBe false
    }

    // --- reconcileSecondary (behavior-preserving, #2389) ---

    @Test
    fun `secondary FAT big mismatch prefers File`() {
        val result = StorageSpaceReconcile.reconcileSecondary(
            statsTotal = 256 * GB,
            statsFree = 4 * GB,
            fileTotal = 128 * GB,
            fileFree = 4 * GB,
            isFatUuid = true,
        )
        result.total shouldBe 128 * GB
        result.free shouldBe 4 * GB
        result.usedFileFallback shouldBe true
    }

    @Test
    fun `secondary FAT small mismatch keeps stats`() {
        val result = StorageSpaceReconcile.reconcileSecondary(
            statsTotal = 130 * GB,
            statsFree = 52 * GB,
            fileTotal = 128 * GB,
            fileFree = 50 * GB,
            isFatUuid = true,
        )
        result.total shouldBe 130 * GB
        result.usedFileFallback shouldBe false
    }

    @Test
    fun `secondary non-FAT big mismatch keeps stats`() {
        // Non-FAT UUIDs are trusted even on large disagreement — the primary guard does not apply here.
        val result = StorageSpaceReconcile.reconcileSecondary(
            statsTotal = 256 * GB,
            statsFree = 100 * GB,
            fileTotal = 128 * GB,
            fileFree = 64 * GB,
            isFatUuid = false,
        )
        result.total shouldBe 256 * GB
        result.usedFileFallback shouldBe false
    }
}
