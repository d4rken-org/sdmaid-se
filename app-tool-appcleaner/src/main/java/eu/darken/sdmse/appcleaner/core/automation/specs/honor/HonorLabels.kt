package eu.darken.sdmse.appcleaner.core.automation.specs.honor

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.getLocales
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class HonorLabels @Inject constructor(
    private val aospLabels: AOSPLabels
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = aospLabels.getStorageEntryDynamic(acsContext)
        // HONOR/PTP-N49EEA/HNPTPX:16/HONORPTP-N49/10.0.0.140C431E6R4P2:user/release-keys
        // Honor's Settings APK strips `storage_settings_for_app`; fall back to the legacy
        // `storage_settings` resource which resolves to the same on-screen text on those ROMs.
        .append { acsContext.getStrings(SETTINGS_PKG, setOf("storage_settings")) }

    fun getStorageEntryStatic(acsContext: AutomationExplorer.Context): Set<String> = acsContext.getLocales()
        .map { it.language to it.script }
        .mapNotNull { (lang, _) ->
            when {
                "pl".toLang() == lang -> setOf(
                    // HONOR/PGT-N19EEA/HNPGT:13/HONORPGT-N49/7.1.0.176C431E3R2P3:user/release-keys
                    "Pamięć",
                )

                "nl".toLang() == lang -> setOf(
                    // HONOR/PTP-N49EEA/HNPTPX:16/HONORPTP-N49/10.0.0.140C431E6R4P2:user/release-keys
                    "Opslag",
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
        .append { aospLabels.getClearCacheStatic(acsContext) }
        .toSet()

    companion object {
        private val TAG: String = logTag("AppCleaner", "Automation", "Honor", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}