package eu.darken.sdmse.appcleaner.core.automation.specs.oneplus

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class OnePlusLabels31Plus @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onePlusLabels29Plus: OnePlusLabels29Plus,
) : AutomationLabelSource {

    fun getStorageEntryLabel(): String? = context.get3rdPartyString(
        SETTINGS_PKG,
        "storage_use"
    ).also { log(TAG) { "getStorageEntryLabel(): $it" } }

    fun getStorageEntryLabels(lang: String, script: String): Collection<String> = when {
        "en".toLang() == lang -> setOf(
            // Guessed based on AOSP usage
            "Storage & cache",
            // Guessed based on AOSP usage
            "Storage and cache",
            // Guessed based on AOSP usage
            "Storage usage"
        )

        "de".toLang() == lang -> setOf(
            // OnePlus/OnePlus9Pro/OnePlus9Pro:13/TP1A.220905.001/R.12ee130-1f9aa-ffaae:user/release-keys
            "Speicher und Cache",
            // Guessed based on AOSP usage
            "Speicher & Cache",
        )

        else -> emptySet()
    }.tryAppend { onePlusLabels29Plus.getStorageEntryLabels(lang, script) }

    fun getClearCacheDynamic(): Set<String>? = onePlusLabels29Plus.getClearCacheDynamic()

    fun getClearCacheStatic(lang: String, script: String): Set<String> =
        onePlusLabels29Plus.getClearCacheStatic(lang, script)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "OnePlus", "Labels", "31Plus")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }

}
