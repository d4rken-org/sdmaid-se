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

    suspend fun isAndroidTV(): Boolean {
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

    suspend fun isLineageROM(): Boolean = Build.DISPLAY.lowercase(Locale.ROOT).contains("lineage")
            || Build.PRODUCT.lowercase(Locale.ROOT).contains("lineage")

    suspend fun isCustomROM() = isLineageROM()
}