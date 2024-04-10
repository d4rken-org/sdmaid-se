package eu.darken.sdmse.appcleaner.core.automation.specs.realme

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels14Plus
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class RealmeLabels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val labels14Plus: AOSPLabels14Plus,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(): Set<String> = setOf(
        "storage_use"
    ).getAsStringResources(context, SETTINGS_PKG)

    fun getStorageEntryLabels(lang: String, script: String): Set<String> = when {
        "en".toLang() == lang -> setOf(
            "Storage usage",
            // https://github.com/d4rken-org/sdmaid-se/issues/744
            "Storage & cache"
        )

        "de".toLang() == lang -> setOf("Speichernutzung")
        "fil".toLang() == lang -> setOf("Paggamit ng storage")
        "ru".toLang() == lang -> setOf("Использование памяти")
        "es".toLang() == lang -> setOf(
            // realme/RMX1993EEA/RMX1993L1:11/RKQ1.201112.002/1621946429926:user/release-keys
            "Uso de almacenamiento",
            // realme/RMX2155EEA/RMX2155L1:11/RP1A.200720.011/1634016814456:user/release-keys
            "Uso del almacenamiento"
        )

        "ru".toLang() == lang -> setOf(
            // realme/RMX3301EEA/RED8ACL1:12/SKQ1.211019.001/S.202203141808:user/release-keys
            "Utilizzo memoria"
        )

        else -> emptySet()
    }.tryAppend { labels14Plus.getStorageEntryStatic(lang, script) }

    fun getClearCacheDynamic(): Set<String> = setOf(
        "clear_cache_btn_text"
    ).getAsStringResources(context, SETTINGS_PKG)

    fun getClearCacheLabels(lang: String, script: String): Set<String> = when {
        "es".toLang() == lang -> setOf(
            // realme/RMX2155EEA/RMX2155L1:11/RP1A.200720.011/1634016814456:user/release-keys
            "Borrar caché"
        )

        "it".toLang() == lang -> setOf("Cancella cache")
        else -> emptySet()
    }.tryAppend { labels14Plus.getClearCacheStatic(lang, script) }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Realme", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}