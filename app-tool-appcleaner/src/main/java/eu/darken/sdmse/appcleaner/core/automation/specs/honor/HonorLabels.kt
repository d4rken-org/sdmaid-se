package eu.darken.sdmse.appcleaner.core.automation.specs.honor

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.getLocales
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
class HonorLabels @Inject constructor(
    private val aospLabels: AOSPLabels
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = aospLabels.getStorageEntryDynamic(acsContext)

    fun getStorageEntryStatic(acsContext: AutomationExplorer.Context): Set<String> = acsContext.getLocales()
        .map { it.language to it.script }
        .mapNotNull { (lang, _) ->
            when {
                "pl".toLang() == lang -> setOf(
                    // HONOR/PGT-N19EEA/HNPGT:13/HONORPGT-N49/7.1.0.176C431E3R2P3:user/release-keys
                    "Pamięć",
                )

                else -> null
            }
        }
        .flatten()
        .append { aospLabels.getStorageEntryStatic(acsContext) }
        .toSet()

    fun getClearCacheDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = aospLabels.getClearCacheDynamic(acsContext)

    fun getClearCacheStatic(acsContext: AutomationExplorer.Context): Set<String> = acsContext.getLocales()
        .map { it.language to it.script }
        .map {
            when {
                else -> emptySet<String>()
            }
        }
        .flatten()
        .append { aospLabels.getStorageEntryStatic(acsContext) }
        .toSet()

    companion object {
        private val TAG: String = logTag("AppCleaner", "Automation", "Honor", "Labels")
    }
}