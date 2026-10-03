package eu.darken.sdmse.stats.ui.spacehistory

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object SpaceHistoryChartMath {

    /** Per 1-px column keeps the first, min-value, max-value and last point, in original order. */
    fun downsampleIndices(xs: FloatArray, values: LongArray): IntArray {
        val size = xs.size
        val out = IntArray(size)
        var count = 0
        var start = 0
        while (start < size) {
            val column = floor(xs[start])
            var end = start + 1
            while (end < size && floor(xs[end]) == column) end++

            if (end - start <= 4) {
                for (i in start until end) out[count++] = i
            } else {
                var minIndex = start
                var maxIndex = start
                for (i in start + 1 until end) {
                    if (values[i] < values[minIndex]) minIndex = i
                    if (values[i] > values[maxIndex]) maxIndex = i
                }
                val low = min(minIndex, maxIndex)
                val high = max(minIndex, maxIndex)
                val last = end - 1
                out[count++] = start
                if (low > start) out[count++] = low
                if (high > low) out[count++] = high
                if (last > high) out[count++] = last
            }
            start = end
        }
        return out.copyOf(count)
    }

    fun interpolateUsed(times: LongArray, used: LongArray, t: Long): Double? {
        if (times.isEmpty()) return null

        var low = 0
        var high = times.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (times[mid] > t) high = mid else low = mid + 1
        }
        val before = low - 1
        val after = low

        return when {
            before >= 0 && after < times.size -> {
                val t0 = times[before]
                val t1 = times[after]
                val v0 = used[before].toDouble()
                val v1 = used[after].toDouble()
                val fraction = if (t1 == t0) 0.5 else (t - t0).toDouble() / (t1 - t0).toDouble()
                v0 + fraction * (v1 - v0)
            }

            before >= 0 -> used[before].toDouble()
            else -> used[after].toDouble()
        }
    }

    fun yFraction(value: Double, min: Long, spread: Long): Float = if (spread <= 0L) {
        0.5f
    } else {
        ((value - min) / spread).toFloat()
    }

    fun includeYear(start: LocalDate, end: LocalDate, today: LocalDate): Boolean =
        start.year != end.year || end.year != today.year
}
