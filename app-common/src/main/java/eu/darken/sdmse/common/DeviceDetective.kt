package eu.darken.sdmse.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.*
import javax.inject.Inject

@Reusable
class DeviceDetective @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isAndroidTV(): Boolean {
        val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true

        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true

        return false
    }

    suspend fun checkManufactor(name: String): Boolean {
        return Build.MANUFACTURER.lowercase(Locale.ROOT) == name
    }

    suspend fun isSamsungDevice(): Boolean = checkManufactor("samsung")

    suspend fun isAlcatel(): Boolean = Build.BRAND?.lowercase() == "alcatel"

    suspend fun isOppo(): Boolean = Build.MANUFACTURER.lowercase() == "oppo"

    suspend fun isMeizu(): Boolean = Build.MANUFACTURER.lowercase() == "meizu"

    suspend fun isHuawei(): Boolean = Build.MANUFACTURER.lowercase() == "huawei"

    suspend fun isLGE(): Boolean = Build.MANUFACTURER.lowercase() == "lge"

    suspend fun isXiaomi(): Boolean = Build.MANUFACTURER.lowercase() == "Xiaomi".lowercase()

    suspend fun isNubia(): Boolean = Build.MANUFACTURER.lowercase() == "nubia"

    suspend fun isOnePlus(): Boolean = Build.MANUFACTURER.lowercase() == "OnePlus".lowercase()

    suspend fun isVivo(): Boolean = Build.MANUFACTURER.lowercase() == "vivo"

    suspend fun isLineageROM(): Boolean = Build.DISPLAY.lowercase().contains("lineage")
            || Build.PRODUCT.lowercase().contains("lineage")

    suspend fun isCustomROM() = isLineageROM()
}