package eu.darken.sdmse.common

import android.os.Build

// Can't be const because that prevents them from being mocked in tests
@Suppress("MayBeConstant")
object BuildWrap {
    val FINGERPRINT: String
        get() = Build.FINGERPRINT

    val MANUFACTOR: String
        get() = Build.MANUFACTURER

    val VERSION = VersionWrap

    object VersionWrap {
        val SDK_INT
            get() = Build.VERSION.SDK_INT
        val PREVIEW_SDK_INT
            get() = Build.VERSION.PREVIEW_SDK_INT
        val CODENAME
            get() = Build.VERSION.CODENAME
    }
}

fun hasApiLevel(level: Int): Boolean = when {
    BuildWrap.VERSION.SDK_INT >= level -> true
    level == 34 && BuildWrap.VERSION.CODENAME == "UpsideDownCake" -> true
    level == 35 && BuildWrap.VERSION.CODENAME == "VanillaIceCream" -> true
    else -> false
}

const val UNTESTED_API = 36

inline fun <reified R> ifApiLevel(level: Int, block: () -> R): R? = if (hasApiLevel(level)) block() else null

