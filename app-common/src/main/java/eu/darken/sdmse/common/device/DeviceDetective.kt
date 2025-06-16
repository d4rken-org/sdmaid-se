package eu.darken.sdmse.common.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.isInstalled
import javax.inject.Inject

@Suppress("SameParameterValue")
@Reusable
class DeviceDetective @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init {
        log(TAG, VERBOSE) { "Loaded." }
    }

    private fun isAndroidTV(): Boolean {
        val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true

        val pm = context.packageManager
        @Suppress("DEPRECATION")
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    private fun manufactor(name: String): Boolean {
        return Build.MANUFACTURER.lowercase() == name.lowercase()
    }

    private fun brand(name: String): Boolean {
        return Build.BRAND?.lowercase() == name.lowercase()
    }

    private fun display(name: String): Boolean {
        return Build.DISPLAY?.lowercase()?.contains(name.lowercase()) == true
    }

    private fun product(name: String): Boolean {
        return Build.PRODUCT?.lowercase()?.contains(name.lowercase()) == true
    }

    private fun apps(pkgs: Set<String>) = pkgs.any { context.isInstalled(it) }

    private fun versionStarts(prints: Set<String>) = prints.any {
        Build.VERSION.INCREMENTAL.startsWith(it)
    }

    fun getROMType(): RomType = when {
        isAndroidTV() -> when {
            // #1826, it's a "tv box" but runs a phone-style ROM
            manufactor("UGOOS") -> RomType.AOSP
            else -> RomType.ANDROID_TV
        }

        display("lineage") || product("lineage") || apps(LINEAGE_PKGS) -> RomType.LINEAGE
        // run mostly near-stock Android
        brand("alcatel") -> RomType.ALCATEL
        // Oppo uses ColorOS globally
        manufactor("oppo") && apps(COLOROS_PKGS) -> RomType.COLOROS
        // Flyme OS
        manufactor("meizu") && apps(FLYME_PKGS) -> RomType.FLYMEOS
        // EMUI (global), HarmonyOS (China)
        manufactor("huawei") && apps(MIUI_PKGS) -> RomType.HUAWEI
        // LG UX, last devices run Android, close to AOSP
        manufactor("lge") -> RomType.LGUX

        manufactor("Xiaomi") -> when {
            apps(HYPEROS_PKGS) && versionStarts(HYPEROS_VERSION_STARTS) -> when {
                // HyperOS 1.0 is based on Android 14 / API34, some backports exist (e.g. pissarropro)
                hasApiLevel(33) -> RomType.HYPEROS
                // Otherwise it is likely a false positive MIUI detection
                else -> RomType.MIUI
            }

            apps(MIUI_PKGS) && versionStarts(MIUI_VERSION_STARTS) -> RomType.MIUI
            else -> null
        }

        manufactor("nubia") -> RomType.NUBIA
        // Should be OxygenOS on earlier versions, and later based on ColorOS
        manufactor("OnePlus") -> RomType.OXYGENOS
        // runs Realme UI, which is a fork of ColorOS with minor changes.
        manufactor("realme") -> RomType.REALMEUI
        // One UI
        manufactor("samsung") -> RomType.ONEUI
        // Vivo is either Funtouch OS (global), which is AOSP like, or OriginOS (China), more modified
        manufactor("vivo") -> when {
            // FUNTOUCH: vivo/V2413_EEA/V2413:15/AP3A.240905.015.A2_V000L1/compiler03201816:user/release-keys
            !hasApiLevel(30) // First origin version was with API30/Android11
                    || apps(FUNTOUCH_PKGS)
                    || product("EEA") -> RomType.FUNTOUCHOS

            // ORIGIN: vivo/PD2366/PD2366:14/UP1A.231005.007_MOD1/compiler07161632:user/release-keys
            // ORIGIN: vivo/PD2454/PD2454:15/AP3A.240905.015.A2_V000L1/compiler250517195248:user/release-keys
            else -> RomType.ORIGINOS
        }
        // Earlier ROMs pre Android 12 run EMUI, Android 13+ is MagicOS
        manufactor("HONOR") -> RomType.HONOR
        // Minimal skin, some preinstalled apps and tweaks, overall, it's near-stock Android.
        manufactor("DOOGEE") -> RomType.DOOGEE
        // OUKITEL/OT5_EEA/OT5:13/TP1A.220624.014/20240528:user/release-keys
        // Stock ROM for the OUKITEL OT5 (European variant)
        manufactor("OUKITEL") -> RomType.OUKITEL
        else -> null
    } ?: RomType.AOSP

    companion object {

        private val MIUI_VERSION_STARTS = setOf(
            "V10",
            // xiaomi/cactus/cactus:9/PPR1.180610.011/V11.0.8.0.PCBMIXM:user/release-keys
            "V11",
            // Xiaomi/raphael_eea/raphael:10/QKQ1.190825.002/V12.0.1.0.QFKEUXM:user/release-keys
            "V12",
            // Xiaomi/venus_eea/venus:12/SKQ1.211006.001/V13.0.1.0.SKBEUXM:user/release-keys
            "V13",
            // Xiaomi/plato_id/plato:13/TP1A.220624.014/V14.0.1.0.TLQIDXM:user/release-keys
            "V14",
        )
        private val MIUI_PKGS = setOf(
            "com.miui.securitycenter"
        )

        val HYPEROS_VERSION_STARTS = setOf(
            // POCO/mondrian_global/mondrian:14/UKQ1.230804.001/V816.0.1.0.UMNMIXM:user/release-keys
            // Xiaomi/aristotle_eea/aristotle:14/UP1A.230905.011/V816.0.17.0.UMFEUXM:user/release-keys
            "V816.",
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
        private val FUNTOUCH_PKGS = setOf(
            "com.funtouch.uiengine"
        )
        private val TAG = logTag("DeviceDetective")
    }
}