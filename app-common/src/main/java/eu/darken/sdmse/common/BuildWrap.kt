package eu.darken.sdmse.common

import android.os.Build

// Can't be const because that prevents them from being mocked in tests
@Suppress("MayBeConstant")
object BuildWrap {

    val VERSION = VersionWrap

    object VersionWrap {
        val SDK_INT = Build.VERSION.SDK_INT
    }
}

fun hasApiLevel(level: Int): Boolean = BuildWrap.VERSION.SDK_INT >= level

inline fun <reified R> ifApiLevel(level: Int, block: () -> R): R? = if (hasApiLevel(level)) block() else null
