package eu.darken.sdmse.stats.ui.spacehistory

import io.kotest.matchers.collections.shouldBeStrictlyIncreasing
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.LocalDate
import kotlin.math.floor

class SpaceHistoryChartMathTest : BaseTest() {

    private fun downsample(xs: List<Float>, values: List<Long>): List<Int> =
        SpaceHistoryChartMath.downsampleIndices(xs.toFloatArray(), values.toLongArray()).toList()

    private fun interpolate(times: List<Long>, used: List<Long>, t: Long): Double? =
        SpaceHistoryChartMath.interpolateUsed(times.toLongArray(), used.toLongArray(), t)

    @Test
    fun `downsampling keeps everything when no column holds more than 4 points`() {
        val xs = listOf(0f, 0.2f, 0.5f, 0.9f, 1f, 1.5f, 2.5f, 3f, 3.1f, 3.2f, 3.3f, 10f)
        val values = xs.indices.map { (it * 7L) % 5L }

        downsample(xs, values) shouldBe xs.indices.toList()
    }

    @Test
    fun `downsampling empty and single inputs`() {
        downsample(emptyList(), emptyList()) shouldBe emptyList()
        downsample(listOf(5f), listOf(42L)) shouldBe listOf(0)
    }

    @Test
    fun `dense column keeps first, min, max and last`() {
        val xs = (0 until 100).map { 10f + it / 100f }
        val values = (0 until 100).map { 1_000L + it }.toMutableList().apply {
            this[30] = 5L
            this[70] = 9_999L
        }

        downsample(xs, values) shouldBe listOf(0, 30, 70, 99)
    }

    @Test
    fun `dense column keeps max before min in original order`() {
        val xs = (0 until 100).map { 10f + it / 100f }
        val values = (0 until 100).map { 1_000L }.toMutableList().apply {
            this[20] = 9_999L
            this[60] = 5L
        }

        downsample(xs, values) shouldBe listOf(0, 20, 60, 99)
    }

    @Test
    fun `dense column deduplicates coinciding extremes`() {
        val xs = (0 until 100).map { 10f + it / 100f }

        downsample(xs, (0 until 100).map { it.toLong() }) shouldBe listOf(0, 99)
        downsample(xs, (0 until 100).map { 100L - it }) shouldBe listOf(0, 99)
        downsample(xs, (0 until 100).map { 7L }) shouldBe listOf(0, 99)
    }

    @Test
    fun `mixed density keeps bounds, stays ascending and within 4 per column`() {
        val xs = buildList {
            add(0f)
            repeat(50) { add(1f + it / 50f) }
            add(2.5f)
            repeat(200) { add(5f + it / 200f) }
            add(7f)
            add(7.5f)
        }
        val values = xs.indices.map { ((it * 7919L) % 1_000L) }

        val kept = downsample(xs, values)

        kept.first() shouldBe 0
        kept.last() shouldBe xs.lastIndex
        kept.shouldBeStrictlyIncreasing()
        val columns = xs.map { floor(it) }.distinct().size
        kept.size shouldBeLessThanOrEqual 4 * columns
        kept.groupBy { floor(xs[it]) }.values.forEach { it.size shouldBeLessThanOrEqual 4 }

        val denseRange = 52 until 252
        val denseKept = kept.filter { it in denseRange }
        denseKept.first() shouldBe denseRange.first
        denseKept.last() shouldBe denseRange.last
        denseKept.contains(denseRange.minBy { values[it] }) shouldBe true
        denseKept.contains(denseRange.maxBy { values[it] }) shouldBe true
    }

    @Test
    fun `interpolation on empty input is null`() {
        interpolate(emptyList(), emptyList(), 5L).shouldBeNull()
    }

    @Test
    fun `interpolation outside the range clamps to the edge values`() {
        val times = listOf(100L, 200L, 300L)
        val used = listOf(10L, 20L, 40L)

        interpolate(times, used, 50L) shouldBe 10.0
        interpolate(times, used, 400L) shouldBe 40.0
    }

    @Test
    fun `interpolation between two points is linear`() {
        val times = listOf(100L, 200L, 300L)
        val used = listOf(10L, 20L, 40L)

        interpolate(times, used, 150L) shouldBe 15.0
        interpolate(times, used, 225L) shouldBe 25.0
    }

    @Test
    fun `interpolation on an exact sample uses that sample`() {
        val times = listOf(100L, 200L, 300L)
        val used = listOf(10L, 20L, 40L)

        interpolate(times, used, 100L) shouldBe 10.0
        interpolate(times, used, 200L) shouldBe 20.0
        interpolate(times, used, 300L) shouldBe 40.0
    }

    @Test
    fun `interpolation on a duplicate timestamp run uses the last sample of the run`() {
        interpolate(listOf(100L, 100L, 101L), listOf(10L, 20L, 90L), 100L) shouldBe 20.0
    }

    @Test
    fun `interpolation with duplicates at start, middle and end`() {
        val times = listOf(100L, 100L, 200L, 200L, 200L, 300L, 300L)
        val used = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L)

        interpolate(times, used, 50L) shouldBe 1.0
        interpolate(times, used, 100L) shouldBe 2.0
        interpolate(times, used, 150L) shouldBe 2.5
        interpolate(times, used, 200L) shouldBe 5.0
        interpolate(times, used, 250L) shouldBe 5.5
        interpolate(times, used, 300L) shouldBe 7.0
        interpolate(times, used, 350L) shouldBe 7.0
    }

    @Test
    fun `interpolation with all-equal timestamps uses the last sample`() {
        val times = listOf(100L, 100L, 100L)
        val used = listOf(1L, 2L, 3L)

        interpolate(times, used, 100L) shouldBe 3.0
        interpolate(times, used, 99L) shouldBe 1.0
    }

    @Test
    fun `yFraction without spread is centered`() {
        SpaceHistoryChartMath.yFraction(0.0, 0L, 0L) shouldBe 0.5f
        SpaceHistoryChartMath.yFraction(64_000_000_000.0, 64_000_000_000L, 0L) shouldBe 0.5f
    }

    @Test
    fun `yFraction for degenerate series is finite`() {
        val singleton = longArrayOf(64_000_000_000L)
        val constant = longArrayOf(5_000L, 5_000L, 5_000L)

        listOf(singleton, constant).forEach { series ->
            val min = series.min()
            val spread = series.max() - min
            series.forEach { SpaceHistoryChartMath.yFraction(it.toDouble(), min, spread).isFinite() shouldBe true }
            val marker = SpaceHistoryChartMath.interpolateUsed(LongArray(series.size) { it.toLong() }, series, 0L)!!
            SpaceHistoryChartMath.yFraction(marker, min, spread).isFinite() shouldBe true
        }

        SpaceHistoryChartMath.interpolateUsed(LongArray(0), LongArray(0), 0L).shouldBeNull()
    }

    @Test
    fun `yFraction keeps small deltas at large byte values`() {
        val min = 64_000_000_000L
        val spread = 3L

        SpaceHistoryChartMath.yFraction(min.toDouble(), min, spread) shouldBe 0f
        SpaceHistoryChartMath.yFraction((min + 1).toDouble(), min, spread).toDouble() shouldBe
            (1.0 / 3.0 plusOrMinus 1e-6)
        SpaceHistoryChartMath.yFraction((min + 3).toDouble(), min, spread) shouldBe 1f
    }

    @Test
    fun `includeYear is false within the current year`() {
        val today = LocalDate.parse("2026-10-03")

        SpaceHistoryChartMath.includeYear(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-10-01"),
            today,
        ) shouldBe false
    }

    @Test
    fun `includeYear is true when crossing new year`() {
        SpaceHistoryChartMath.includeYear(
            LocalDate.parse("2025-12-20"),
            LocalDate.parse("2026-01-05"),
            LocalDate.parse("2026-01-05"),
        ) shouldBe true
    }

    @Test
    fun `includeYear is true when the span lies in a past year`() {
        SpaceHistoryChartMath.includeYear(
            LocalDate.parse("2025-03-01"),
            LocalDate.parse("2025-09-01"),
            LocalDate.parse("2026-10-03"),
        ) shouldBe true
    }
}
