package eu.darken.sdmse.appcleaner.core.automation.specs.lge

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
class LGELabels @Inject constructor(
    private val aospLabels: AOSPLabels,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("storage_settings"))

    fun getStorageEntryLabels(acsContext: AutomationExplorer.Context) = acsContext.getLocales()
        .map { it.language to it.script }
        .mapNotNull { (lang, _) ->
            when {
                // https://github.com/d4rken/sdmaid-public/issues/3577
                // lge/judyln_lao_com/judyln:10/QKQ1.191222.002/2011502001bac:user/release-keys
                "en".toLang() == lang -> {
                    listOf("Storage")
                }
                // LM-G810 10/QKQ1.200614.002
                "eu".toLang() == lang -> {
                    listOf("Memoria")
                }
                // lge/mh2lm/mh2lm:10/QKQ1.200216.002/20283101285e8:user/release-keys
                "pt".toLang() == lang -> {
                    listOf("Armazenamento")
                }

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
        .map { it.language to it.country }
        .mapNotNull { (lang, _) ->
            when {
                // lge/mh2lm/mh2lm:10/QKQ1.200216.002/20283101285e8:user/release-keys
                "pt".toLang() == lang -> listOf("Apagar cache")

                else -> null
            }
        }
        .flatten()
        .append { aospLabels.getClearCacheStatic(acsContext) }
        .toSet()

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "LGE", "Labels")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }
}