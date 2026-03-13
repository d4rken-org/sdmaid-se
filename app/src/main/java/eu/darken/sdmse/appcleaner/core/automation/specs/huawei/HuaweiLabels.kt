package eu.darken.sdmse.appcleaner.core.automation.specs.huawei

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.getLocales
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Suppress("IntroduceWhenSubject")
@Reusable
class HuaweiLabels @Inject constructor(
    private val aospLabels: AOSPLabels,
) : AppCleanerLabelSource {


    fun getStorageEntryDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("storage_settings"))

    fun getStorageEntryLabels(acsContext: AutomationExplorer.Context) = acsContext.getLocales()
        .map { it.language to it.script }
        .mapNotNull { (lang, _) ->
            when {
                "en".toLang() == lang -> setOf(
                    // https://github.com/d4rken/sdmaid-public/issues/3576
                    // HONOR/PCT-L29RU/HWPCT:10/HUAWEIPCT-L29/10.0.0.195C10:user/release-keys
                    "Storage"
                )

                "ru".toLang() == lang -> setOf(
                    // https://github.com/d4rken/sdmaid-public/issues/3576
                    // HONOR/PCT-L29RU/HWPCT:10/HUAWEIPCT-L29/10.0.0.195C10:user/release-keys
                    "Память"
                )

                "de".toLang() == lang -> setOf(
                    // HUAWEI/VOG-L29EEA/HWVOG:10/HUAWEIVOG-L29/10.0.0.168C431:user/release-keys
                    "Speicher"
                )

                "pl".toLang() == lang -> setOf(
                    // HUAWEI/VOG-L29EEA/HWVOG:10/HUAWEIVOG-L29/10.0.0.178C431:user/release-keys
                    "Pamięć"
                )

                "it".toLang() == lang -> setOf(
                    "Spazio di archiviazione e cache",
                    // HONOR/JSN-L21/HWJSN-H:10/HONORJSN-L21/10.0.0.175C432:user/release-keys
                    "Memoria"
                )

                "ca".toLang() == lang -> setOf(
                    // EMUI 11 (Android 10) [Huawei Mate 20 Pro]
                    "Emmagatzematge"
                )

                else -> null
            }
        }
        .flatten()
        .append { aospLabels.getStorageEntryStatic(acsContext) }
        .toSet()

    fun getClearCacheDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("clear_cache_btn_text"))

    fun getClearCacheLabels(acsContext: AutomationExplorer.Context): Collection<String> = acsContext.getLocales()
        .map { it.language to it.script }
        .mapNotNull { (lang, _) ->
            when {
                "ca".toLang() == lang -> setOf(
                    // EMUI 11 (Android 10) [Huawei Mate 20 Pro]
                    "Esborrar la memòria cau"
                )

                else -> null
            }
        }
        .flatten()
        .append { aospLabels.getClearCacheStatic(acsContext) }
        .toSet()

    companion object {
        private val TAG: String = logTag("AppCleaner", "Automation", "Huawei", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}