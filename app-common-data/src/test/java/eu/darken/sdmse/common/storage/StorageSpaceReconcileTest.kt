package eu.darken.sdmse.common.storage

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StorageSpaceReconcileTest : BaseTest() {

    // ~1TB device reported as 2TB, free correct. Reproduces realme/ColorOS A15 report.
    private val TB = 1_000_000_000_000L
    private val GB = 1_000_000_000L

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
