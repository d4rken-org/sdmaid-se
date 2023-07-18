package eu.darken.sdmse.common.progress

import android.content.Context
import android.text.format.Formatter
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.easterEggProgressMsg
import kotlinx.coroutines.flow.Flow
import kotlin.math.ceil

interface Progress {

    data class Data(
        val icon: CaDrawable? = null,
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

    sealed interface Count {
        val current: Long
        val max: Long
        fun displayValue(context: Context): String?

        data class Percent(override val current: Long, override val max: Long) : Count {

            constructor(current: Int, max: Int) : this(current.toLong(), max.toLong())
            constructor(max: Int) : this(0, max)
            constructor(max: Long) : this(0, max)

            override fun displayValue(context: Context): String {
                if (current == 0L && max == 0L) return "NaN"
                if (current == 0L) return "0%"
                return "${ceil(((current.toDouble() / max.toDouble()) * 100)).toInt()}%"
            }

            fun increment(): Percent {
                return Percent(current + 1, max)
            }
        }

        class Counter(override val current: Long, override val max: Long) : Count {

            constructor(current: Int, max: Int) : this(current.toLong(), max.toLong())
            constructor(max: Int) : this(0, max)
            constructor(max: Long) : this(0, max)

            override fun displayValue(context: Context): String = "$current/$max"

            fun increment() = Counter(current + 1, max)
        }

        data class Size(override val current: Long, override val max: Long) : Count {
            override fun displayValue(context: Context): String {
                val curSize = Formatter.formatShortFileSize(context, current)
                val maxSize = Formatter.formatShortFileSize(context, max)
                return "$curSize/$maxSize"
            }
        }

        data class Indeterminate(override val current: Long = 0, override val max: Long = 0) : Count {
            override fun displayValue(context: Context): String = ""
        }

        data class None(override val current: Long = -1, override val max: Long = -1) : Count {
            override fun displayValue(context: Context): String? = null
        }
    }

    companion object {
        val DEFAULT_STATE = Data(
            primary = eu.darken.sdmse.common.R.string.general_progress_loading.toCaString(),
            secondary = easterEggProgressMsg.toCaString(),
            count = Count.Indeterminate()
        )
    }

}