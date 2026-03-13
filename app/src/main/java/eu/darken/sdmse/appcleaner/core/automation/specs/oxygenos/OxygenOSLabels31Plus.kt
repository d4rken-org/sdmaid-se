package eu.darken.sdmse.appcleaner.core.automation.specs.oxygenos

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.getLocales
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class OxygenOSLabels31Plus @Inject constructor(
    private val oxygenOSLabels29Plus: OxygenOSLabels29Plus,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(
        acsContext: AutomationExplorer.Context,
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("storage_use"))

    fun getStorageEntryLabels(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getLocales()
        .map { it.language to it.script }
        .mapNotNull { (lang, _) ->
            when {
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

                else -> null
            }
        }
        .flatten()
        .append { oxygenOSLabels29Plus.getStorageEntryLabels(acsContext) }
        .toSet()

    fun getClearCacheDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = oxygenOSLabels29Plus.getClearCacheDynamic(acsContext)

    fun getClearCacheStatic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = oxygenOSLabels29Plus.getClearCacheStatic(acsContext)

    companion object {
        private val TAG: String = logTag("AppCleaner", "Automation", "OnePlus", "Labels", "31Plus")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }

}
