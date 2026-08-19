package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant

internal class CombinedDeltaCalculationTest : BaseTest() {

    private fun snapshot(
        storageId: String,
        recordedAt: Instant,
        used: Long,
        capacity: Long = 1000L,
    ) = SpaceSnapshotEntity(
        storageId = storageId,
        recordedAt = recordedAt,
        spaceFree = capacity - used,
        spaceCapacity = capacity,
    )

    private val t0 = Instant.parse("2026-06-01T00:00:00Z")
    private val t1 = Instant.parse("2026-06-02T00:00:00Z")
    private val t2 = Instant.parse("2026-06-03T00:00:00Z")

    @Test
    fun `empty list has no trend`() {
        calculateCombinedDelta(emptyList()).shouldBeNull()
    }

    @Test
    fun `single snapshot per storage has no trend`() {
        calculateCombinedDelta(
            listOf(
                snapshot("a", t0, used = 100),
                snapshot("b", t0, used = 200),
            )
        ).shouldBeNull()
    }

    @Test
    fun `positive delta when usage grows`() {
        calculateCombinedDelta(
            listOf(
                snapshot("a", t0, used = 100),
                snapshot("a", t1, used = 250),
            )
        ) shouldBe 150L
    }

    @Test
    fun `negative delta when usage shrinks`() {
        calculateCombinedDelta(
            listOf(
                snapshot("a", t0, used = 250),
                snapshot("a", t1, used = 100),
            )
        ) shouldBe -150L
    }

    @Test
    fun `zero delta is a trend, not absence of one`() {
        calculateCombinedDelta(
            listOf(
                snapshot("a", t0, used = 100),
                snapshot("a", t1, used = 100),
            )
        ) shouldBe 0L
    }

    @Test
    fun `deltas sum across storages, single-snapshot storages ignored`() {
        calculateCombinedDelta(
            listOf(
                snapshot("a", t0, used = 100),
                snapshot("a", t2, used = 300),
                snapshot("b", t0, used = 500),
                snapshot("b", t1, used = 450),
                snapshot("c", t0, used = 999),
            )
        ) shouldBe 150L
    }

    @Test
    fun `delta uses chronological order regardless of input order`() {
        calculateCombinedDelta(
            listOf(
                snapshot("a", t2, used = 300),
                snapshot("a", t0, used = 100),
                snapshot("a", t1, used = 700),
            )
        ) shouldBe 200L
    }

    @Test
    fun `a cleanup inside the window cannot report a filling device as emptying`() {
        // A task forces a snapshot, so a window that ends right after a cleanup shows sharply more
        // free space. First-to-last read that as -50, i.e. "usage shrinking", on a device that had
        // gained 100 per day all week.
        val filling = (0L..6L).map { day ->
            snapshot(
                storageId = "a",
                recordedAt = t0.plus(Duration.ofDays(day)),
                used = if (day == 6L) 19_950L else 20_000L + 100L * day,
                capacity = 100_000L,
            )
        }
        calculateCombinedDelta(filling) shouldBe 600L
    }
}
