package eu.darken.sdmse.common.progress

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.format.Formatter
import eu.darken.sdmse.common.castring.CaString
import kotlinx.coroutines.flow.Flow
import kotlin.math.ceil

interface Progress {
    data class Data(
        val icon: Drawable? = null,
        val primary: CaString = CaString.EMPTY,
        val secondary: CaString = CaString.EMPTY,
        val count: Count = Count.None(),
        val extra: Any? = null
    )

    interface Host {
        val progress: Flow<Data?>
    }

    interface Client {
        fun updateProgress(update: (Data?) -> Data?)
    }

    sealed class Count(val current: Long, val max: Long) {
        abstract fun displayValue(context: Context): String?

        class Percent(current: Long, max: Long) : Count(current, max) {

            constructor(current: Int, max: Int) : this(current.toLong(), max.toLong())

            constructor(current: Long) : this(current, 100)

            override fun displayValue(context: Context): String {
                if (current == 0L && max == 0L) return "NaN"
                if (current == 0L) return "0%"
                return "${ceil(((current.toDouble() / max.toDouble()) * 100)).toInt()}%"
            }
        }

        class Size(current: Long, max: Long) : Count(current, max) {
            override fun displayValue(context: Context): String {
                val curSize = Formatter.formatShortFileSize(context, current)
                val maxSize = Formatter.formatShortFileSize(context, max)
                return "$curSize/$maxSize"
            }
        }

        class Counter(current: Long = 0, max: Long) : Count(current, max) {

            constructor(current: Int = 0, max: Int) : this(current.toLong(), max.toLong())

            override fun displayValue(context: Context): String = "$current/$max"

            fun increment() = Counter(current + 1, max)
        }

        class Indeterminate : Count(0, 0) {
            override fun displayValue(context: Context): String = ""
        }

        class None : Count(-1, -1) {
            override fun displayValue(context: Context): String? = null
        }
    }
}