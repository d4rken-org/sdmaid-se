package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StorageSnapshotDeltaTest : BaseTest() {

    @Test
    fun `detects full clear as SUCCESS`() {
        val pre = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(57340, "57.34 kB"),
                StorageSnapshot.ParsedSize(1160000, "1.16 MB"),
                StorageSnapshot.ParsedSize(143000, "143 kB"),
                StorageSnapshot.ParsedSize(1360000, "1.36 MB"),
            )
        )
        val post = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(57340, "57.34 kB"),
                StorageSnapshot.ParsedSize(1160000, "1.16 MB"),
                StorageSnapshot.ParsedSize(0, "0 B"),
                StorageSnapshot.ParsedSize(1220000, "1.22 MB"),
            )
        )
        compareSnapshots(pre, post) shouldBe DeltaResult.SUCCESS
    }

    @Test
    fun `detects partial clear as SUCCESS`() {
        val pre = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(57340, "57.34 kB"),
                StorageSnapshot.ParsedSize(1160000, "1.16 MB"),
                StorageSnapshot.ParsedSize(500000, "500 kB"),
                StorageSnapshot.ParsedSize(1717340, "1.72 MB"),
            )
        )
        val post = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(57340, "57.34 kB"),
                StorageSnapshot.ParsedSize(1160000, "1.16 MB"),
                StorageSnapshot.ParsedSize(200000, "200 kB"),
                StorageSnapshot.ParsedSize(1417340, "1.42 MB"),
            )
        )
        compareSnapshots(pre, post) shouldBe DeltaResult.SUCCESS
    }

    @Test
    fun `detects already zero as SKIP_SUCCESS`() {
        val snapshot = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(57340, "57.34 kB"),
                StorageSnapshot.ParsedSize(1160000, "1.16 MB"),
                StorageSnapshot.ParsedSize(0, "0 B"),
                StorageSnapshot.ParsedSize(1217340, "1.22 MB"),
            )
        )
        compareSnapshots(snapshot, snapshot) shouldBe DeltaResult.SKIP_SUCCESS
    }

    @Test
    fun `detects no change as NO_CHANGE`() {
        val snapshot = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(57340, "57.34 kB"),
                StorageSnapshot.ParsedSize(1160000, "1.16 MB"),
                StorageSnapshot.ParsedSize(143000, "143 kB"),
                StorageSnapshot.ParsedSize(1360000, "1.36 MB"),
            )
        )
        compareSnapshots(snapshot, snapshot) shouldBe DeltaResult.NO_CHANGE
    }

    @Test
    fun `returns INCONCLUSIVE when all values unparseable`() {
        val snapshot = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(null, "unknown"),
                StorageSnapshot.ParsedSize(null, "data"),
            )
        )
        compareSnapshots(snapshot, snapshot) shouldBe DeltaResult.INCONCLUSIVE
    }

    @Test
    fun `returns INCONCLUSIVE when row count changes`() {
        val pre = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(57340, "57.34 kB"),
                StorageSnapshot.ParsedSize(1160000, "1.16 MB"),
                StorageSnapshot.ParsedSize(143000, "143 kB"),
                StorageSnapshot.ParsedSize(1360000, "1.36 MB"),
            )
        )
        val post = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(57340, "57.34 kB"),
                StorageSnapshot.ParsedSize(1160000, "1.16 MB"),
                StorageSnapshot.ParsedSize(0, "0 B"),
            )
        )
        compareSnapshots(pre, post) shouldBe DeltaResult.INCONCLUSIVE
    }

    @Test
    fun `returns INCONCLUSIVE when pre is empty`() {
        val pre = StorageSnapshot(emptyList())
        val post = StorageSnapshot(
            listOf(StorageSnapshot.ParsedSize(1000, "1 kB")),
        )
        compareSnapshots(pre, post) shouldBe DeltaResult.INCONCLUSIVE
    }

    @Test
    fun `returns INCONCLUSIVE when post is empty`() {
        val pre = StorageSnapshot(
            listOf(StorageSnapshot.ParsedSize(1000, "1 kB")),
        )
        val post = StorageSnapshot(emptyList())
        compareSnapshots(pre, post) shouldBe DeltaResult.INCONCLUSIVE
    }

    @Test
    fun `returns INCONCLUSIVE when both are empty`() {
        val empty = StorageSnapshot(emptyList())
        compareSnapshots(empty, empty) shouldBe DeltaResult.INCONCLUSIVE
    }

    @Test
    fun `single value decrease is SUCCESS`() {
        val pre = StorageSnapshot(listOf(StorageSnapshot.ParsedSize(5000, "5 kB")))
        val post = StorageSnapshot(listOf(StorageSnapshot.ParsedSize(1000, "1 kB")))
        compareSnapshots(pre, post) shouldBe DeltaResult.SUCCESS
    }

    @Test
    fun `single value increase is NO_CHANGE`() {
        val pre = StorageSnapshot(listOf(StorageSnapshot.ParsedSize(1000, "1 kB")))
        val post = StorageSnapshot(listOf(StorageSnapshot.ParsedSize(5000, "5 kB")))
        compareSnapshots(pre, post) shouldBe DeltaResult.NO_CHANGE
    }

    @Test
    fun `mixed parseable and unparseable still detects decrease`() {
        val pre = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(null, "unknown"),
                StorageSnapshot.ParsedSize(5000, "5 kB"),
            )
        )
        val post = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(null, "unknown"),
                StorageSnapshot.ParsedSize(1000, "1 kB"),
            )
        )
        compareSnapshots(pre, post) shouldBe DeltaResult.SUCCESS
    }

    @Test
    fun `mixed parseable and unparseable detects no change`() {
        val snapshot = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(null, "unknown"),
                StorageSnapshot.ParsedSize(5000, "5 kB"),
            )
        )
        compareSnapshots(snapshot, snapshot) shouldBe DeltaResult.NO_CHANGE
    }

    @Test
    fun `SUCCESS takes priority over SKIP_SUCCESS when both apply`() {
        val pre = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(0, "0 B"),
                StorageSnapshot.ParsedSize(5000, "5 kB"),
            )
        )
        val post = StorageSnapshot(
            listOf(
                StorageSnapshot.ParsedSize(0, "0 B"),
                StorageSnapshot.ParsedSize(1000, "1 kB"),
            )
        )
        compareSnapshots(pre, post) shouldBe DeltaResult.SUCCESS
    }
}
