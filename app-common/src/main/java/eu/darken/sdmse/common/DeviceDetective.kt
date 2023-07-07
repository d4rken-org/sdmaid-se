package eu.darken.sdmse.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private suspend fun checkManufactor(name: String): Boolean {
        return Build.MANUFACTURER.lowercase() == name.lowercase()
    }

    private suspend fun checkBrand(name: String): Boolean {
        return Build.BRAND?.lowercase() == name.lowercase()
    }

    suspend fun isSamsungDevice(): Boolean = checkManufactor("samsung")

    suspend fun isAlcatel(): Boolean = checkBrand("alcatel")

    suspend fun isOppo(): Boolean = checkManufactor("oppo")

    suspend fun isMeizu(): Boolean = checkManufactor("meizu")

    suspend fun isHuawei(): Boolean = checkManufactor("huawei")

    suspend fun isLGE(): Boolean = checkManufactor("lge")

    suspend fun isXiaomi(): Boolean = checkManufactor("Xiaomi")

    suspend fun isPoco(): Boolean = checkManufactor("POCO")

    suspend fun isNubia(): Boolean = checkManufactor("nubia")

    suspend fun isOnePlus(): Boolean = checkManufactor("OnePlus")

    suspend fun isVivo(): Boolean = checkManufactor("vivo")

    suspend fun isLineageROM(): Boolean = Build.DISPLAY.lowercase().contains("lineage")
            || Build.PRODUCT.lowercase().contains("lineage")

    suspend fun isCustomROM() = isLineageROM()
}