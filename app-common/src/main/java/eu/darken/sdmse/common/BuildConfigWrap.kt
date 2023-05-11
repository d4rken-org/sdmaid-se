package eu.darken.sdmse.common

import android.util.Log
import androidx.annotation.Keep
import java.lang.reflect.Field


// Can't be const because that prevents them from being mocked in tests
@Suppress("MayBeConstant")
@Keep
object BuildConfigWrap {
    val APPLICATION_ID = getBuildConfigValue("PACKAGENAME") as String
    val DEBUG: Boolean = getBuildConfigValue("DEBUG") as Boolean
    val BUILD_TYPE: BuildType = when (val typ = getBuildConfigValue("BUILD_TYPE") as String) {
        "debug" -> BuildType.DEV
        "beta" -> BuildType.BETA
        "release" -> BuildType.RELEASE
        else -> throw IllegalArgumentException("Unknown buildtype: $typ")
    }

    @Keep
    enum class BuildType {
        DEV,
        BETA,
        RELEASE,
        ;
    }

    val FLAVOR: Flavor = when (val flav = getBuildConfigValue("FLAVOR") as String?) {
        "gplay" -> Flavor.GPLAY
        "foss" -> Flavor.FOSS
        null -> Flavor.NONE
        else -> throw IllegalStateException("Unknown flavor: $flav")
    }

    enum class Flavor {
        GPLAY,
        FOSS,
        NONE,
        ;
    }

    val VERSION_CODE: Long = (getBuildConfigValue("VERSION_CODE") as String).toLong()
    val VERSION_NAME: String = getBuildConfigValue("VERSION_NAME") as String
    val GIT_SHA: String = getBuildConfigValue("GITSHA") as String

    val VERSION_DESCRIPTION: String = "v$VERSION_NAME ($VERSION_CODE) ~ $GIT_SHA/$FLAVOR/$BUILD_TYPE"

    private fun getBuildConfigValue(fieldName: String): Any? = try {
        val c = Class.forName("eu.darken.sdmse.BuildConfig")
        val f: Field = c.getField(fieldName).apply {
            isAccessible = true
        }
        f.get(null)
    } catch (e: Exception) {
        Log.e("getBuildConfigValue", "fieldName: $fieldName")
        e.printStackTrace()
        null
    }
}
