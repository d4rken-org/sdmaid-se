package eu.darken.sdmse.appcleaner.core.automation.specs

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.toVisualString
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
                        log(TAG) { "$pkgId: '$resId' -> '${label?.toVisualString()}'" }
                    }
                }
            }

        if (Bugs.isDebug) {
            try {
                logResourceScan()
            } catch (e: Exception) {
                log(TAG, WARN) { "Resource scan failed: ${e.asLog()}" }
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun logResourceScan() {
        SETTINGS_PKGS
            .filter { context.isInstalled(it.name) }
            .forEach { pkgId ->
                Resources.getSystem().configuration.locales.toList().forEach { locale ->
                    val origRes = context.packageManager.getResourcesForApplication(pkgId.name)
                    val config = Configuration(origRes.configuration).apply { setLocale(locale) }
                    @Suppress("DEPRECATION")
                    val localizedRes = Resources(origRes.assets, origRes.displayMetrics, config)

                    val knownId = localizedRes.getIdentifier("clear_cache_btn_text", "string", pkgId.name)
                    if (knownId == 0) return@forEach
                    val typePrefix = knownId and 0xFFFF0000.toInt()

                    var consecutiveMisses = 0
                    var matchCount = 0
                    var scannedCount = 0
                    for (entry in 0..MAX_SCAN_ENTRIES) {
                        val resId = typePrefix or entry
                        try {
                            val name = localizedRes.getResourceEntryName(resId)
                            consecutiveMisses = 0
                            scannedCount++
                            if (SCAN_KEYWORDS.any { name.contains(it, ignoreCase = true) }) {
                                val value = try {
                                    localizedRes.getString(resId)
                                } catch (_: Exception) {
                                    "?"
                                }
                                log(TAG) { "${pkgId.name}: scan found '$name' = '$value'" }
                                matchCount++
                            }
                        } catch (_: Resources.NotFoundException) {
                            consecutiveMisses++
                            if (consecutiveMisses > 500) break
                        }
                    }
                    log(TAG) { "Resource scan complete for ${pkgId.name} [$locale], found $matchCount matches in $scannedCount entries" }
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
        private val RES_IDS_WINDOW_TITLES = setOf(
            "application_info_label",
        )
        private val ALL_RES_IDS =
            RES_IDS_STORAGE + RES_IDS_CLEARDATA + RES_IDS_CLEARCACHE + RES_IDS_DIALOGTITLES + RES_IDS_WINDOW_TITLES
        private const val MAX_SCAN_ENTRIES = 20_000
        private val SCAN_KEYWORDS = setOf("cache", "storage", "clear")
        private val TAG = logTag("Automation", "LabelDebugger")
    }
}