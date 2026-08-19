package eu.darken.sdmse.common

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

// Can't be const because that prevents them from being mocked in tests
@Suppress("MayBeConstant")
object BuildWrap {
    val FINGERPRINT: String
        get() = Build.FINGERPRINT

    val MANUFACTOR: String
        get() = Build.MANUFACTURER

    val BRAND: String?
        get() = Build.BRAND

    val DISPLAY: String?
        get() = Build.DISPLAY

    val PRODUCT: String?
        get() = Build.PRODUCT

    val HARDWARE: String?
        get() = Build.HARDWARE.normalizeBuildValue()

    /**
     * SoC vendor, e.g. "Google", "Qualcomm", "QTI", "Mediatek". Null below API31, where the
     * property does not exist.
     *
     * Free-form vendor-written text, NOT an enum: Qualcomm ships as both "Qualcomm" and "QTI",
     * and MediaTek as both "MediaTek" and "Mediatek". Compare case-insensitively, never by equality.
     */
    val SOC_MANUFACTURER: String?
        get() = ifApiLevel(31) { Build.SOC_MANUFACTURER }.normalizeBuildValue()

    /** SoC model, e.g. "Tensor G3", "SM6375", "MT8781V/NA". Null below API31. */
    val SOC_MODEL: String?
        get() = ifApiLevel(31) { Build.SOC_MODEL }.normalizeBuildValue()

    val VERSION = VersionWrap

    object VersionWrap {
        val SDK_INT
            get() = Build.VERSION.SDK_INT
        val PREVIEW_SDK_INT
            get() = Build.VERSION.PREVIEW_SDK_INT
        val CODENAME
            get() = Build.VERSION.CODENAME
        val INCREMENTAL
            get() = Build.VERSION.INCREMENTAL
    }
}

/**
 * [Build.UNKNOWN] is the documented sentinel for "the vendor did not set this", and some vendors
 * leave the property blank instead. Neither is a usable value, so both read as absent.
 */
internal fun String?.normalizeBuildValue(): String? = this
    ?.trim()
    ?.takeUnless { it.isEmpty() || it.equals(Build.UNKNOWN, ignoreCase = true) }

@ChecksSdkIntAtLeast(parameter = 0)
fun hasApiLevel(level: Int): Boolean = when {
    BuildWrap.VERSION.SDK_INT >= level -> true
    level == 34 && BuildWrap.VERSION.CODENAME == "UpsideDownCake" -> true
    level == 35 && BuildWrap.VERSION.CODENAME == "VanillaIceCream" -> true
    level == 36 && BuildWrap.VERSION.CODENAME == "Baklava" -> true
    level == 37 && BuildWrap.VERSION.CODENAME == "CinnamonBun" -> true
    else -> false
}

const val UNTESTED_API = 37

// lambda = 1: without it lint only knows hasApiLevel() gates something, not that THIS lambda is the
// gated code, and flags API-restricted calls inside it as NewApi.
@ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
inline fun <reified R> ifApiLevel(level: Int, block: () -> R): R? = if (hasApiLevel(level)) block() else null

