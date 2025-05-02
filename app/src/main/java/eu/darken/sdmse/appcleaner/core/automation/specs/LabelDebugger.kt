package eu.darken.sdmse.appcleaner.core.automation.specs

import android.content.Context
import android.content.res.Resources
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.isInstalled
import eu.darken.sdmse.common.locale.toList
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

class LabelDebugger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : AutomationLabelSource {

    suspend fun logAllLabels() {
        log(TAG) { "logAllStorageLabels()" }
        val romType = deviceDetective.getROMType()
        log(TAG) { "ROMTYPE is $romType" }
        SETTINGS_PKGS
            .filter { context.isInstalled(it.name) }
            .forEach { pkgId ->
                Resources.getSystem().configuration.locales.toList().forEach { locale ->
                    ALL_RES_IDS.forEach { resId ->
                        val label = context.get3rdPartyString(pkgId, resId, locale)
                        log(TAG) { "$pkgId: '$resId' -> '$label'" }
                    }
                }
            }
    }

    companion object {
        private val SETTINGS_PKGS = setOf(
            "com.android.settings",
            "com.android.tv.settings",
            "com.miui.securitycenter",
        ).map { it.toPkgId() }
        private val RES_IDS_STORAGE = setOf(
            "storage_settings",
            "storage_settings_for_app",
            "storage_use",
        )
        private val RES_IDS_CLEARCACHE = setOf(
            "clear_cache_btn_text",
            "app_manager_clear_cache"
        )
        private val RES_IDS_CLEARDATA = setOf(
            "app_manager_menu_clear_data",
        )
        private val RES_IDS_DIALOGTITLES = setOf(
            "app_manager_dlg_clear_cache_title",
        )
        private val ALL_RES_IDS = RES_IDS_STORAGE + RES_IDS_CLEARDATA + RES_IDS_CLEARCACHE + RES_IDS_DIALOGTITLES
        private val TAG = logTag("Automation", "LabelDebugger")
    }
}