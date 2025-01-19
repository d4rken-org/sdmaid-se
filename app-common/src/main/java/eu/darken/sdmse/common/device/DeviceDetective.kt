package eu.darken.sdmse.common.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.isInstalled
import javax.inject.Inject

@Reusable
class DeviceDetective @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private fun isAndroidTV(): Boolean {
        val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true

        val pm = context.packageManager
        @Suppress("DEPRECATION")
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    private fun checkManufactor(name: String): Boolean {
        return Build.MANUFACTURER.lowercase() == name.lowercase()
    }

    private fun checkBrand(name: String): Boolean {
        return Build.BRAND?.lowercase() == name.lowercase()
    }

    private fun checkDisplay(name: String): Boolean {
        return Build.DISPLAY?.lowercase()?.contains(name.lowercase()) == true
    }

    private fun checkProduct(name: String): Boolean {
        return Build.PRODUCT?.lowercase()?.contains(name.lowercase()) == true
    }

    private fun hasApp(pkgs: Set<String>) = pkgs.any { context.isInstalled(it) }

    private fun hasFingerPrint(prints: Set<String>) = prints.any {
        Build.VERSION.INCREMENTAL.startsWith(it)
    }

    fun getROMType(): RomType = when {
        isAndroidTV() -> RomType.ANDROID_TV
        checkDisplay("lineage") || checkProduct("lineage") || hasApp(LINEAGE_PKGS) -> RomType.LINEAGE
        checkBrand("alcatel") -> RomType.ALCATEL
        checkManufactor("oppo") && hasApp(COLOROS_PKGS) -> RomType.COLOROS
        checkManufactor("meizu") && hasApp(FLYME_PKGS) -> RomType.FLYME
        checkManufactor("huawei") && hasApp(MIUI_PKGS) -> RomType.HUAWEI
        checkManufactor("lge") -> RomType.LGE
        // HyperOS 1.0 is based on Android 14 / API34, so anything lower is likely a false positive of MIUI
        checkManufactor("Xiaomi") && hasApiLevel(34) && hasApp(HYPEROS_PKGS) && hasFingerPrint(HYPEROS_VERSION_STARTS) -> RomType.HYPEROS
        checkManufactor("Xiaomi") && hasApp(MIUI_PKGS) && hasFingerPrint(MIUI_VERSION_STARTS) -> RomType.MIUI
        checkManufactor("nubia") -> RomType.NUBIA
        checkManufactor("OnePlus") -> RomType.ONEPLUS
        checkManufactor("POCO") -> RomType.POCO
        checkManufactor("realme") -> RomType.REALME
        checkManufactor("samsung") -> RomType.SAMSUNG
        checkManufactor("vivo") -> RomType.VIVO
        checkManufactor("HONOR") -> RomType.HONOR
        else -> RomType.AOSP
    }

    companion object {
        private val MIUI_VERSION_STARTS_LEGACY = setOf(
            "V10",
            "V11",
        )
        private val MIUI_VERSION_STARTS_CURRENT = setOf(
            // Xiaomi/raphael_eea/raphael:10/QKQ1.190825.002/V12.0.1.0.QFKEUXM:user/release-keys
            "V12",
            // Xiaomi/venus_eea/venus:12/SKQ1.211006.001/V13.0.1.0.SKBEUXM:user/release-keys
            "V13",
            // Xiaomi/plato_id/plato:13/TP1A.220624.014/V14.0.1.0.TLQIDXM:user/release-keys
            "V14",
            // POCO/mondrian_global/mondrian:14/UKQ1.230804.001/V816.0.1.0.UMNMIXM:user/release-keys,
            "V816", // wtf poco
        )
        val MIUI_VERSION_STARTS = MIUI_VERSION_STARTS_LEGACY + MIUI_VERSION_STARTS_CURRENT
        private val MIUI_PKGS = setOf(
            "com.miui.securitycenter"
        )

        val HYPEROS_VERSION_STARTS = setOf(
            // OS1.0.12.0.ULLMIXM
            "OS1",
            // Xiaomi/corot_global/corot:15/AP3A.240617.008/OS2.0.6.0.VMLMIXM:user/release-keys
            "OS2",
        )
        private val HYPEROS_PKGS = setOf(
            "com.miui.securitycenter"
        )

        private val FLYME_PKGS = setOf(
            "com.meizu.flyme.update"
        )
        private val COLOROS_PKGS = setOf(
            // Not available on OPPO/CPH2247EEA/OP4F7FL1:11/RKQ1.201105.002/1632415665086:user/release-keys
            "com.coloros.simsettings",
            "com.coloros.filemanager"
        )
        private val LINEAGE_PKGS = setOf(
            "org.lineageos.lineagesettings",
            "lineageos.platform",
            "org.lineageos.settings.device",
        )
        private val TAG = logTag("DeviceDetective")
    }
}