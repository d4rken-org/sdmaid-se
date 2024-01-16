package eu.darken.sdmse.appcleaner.core.automation.specs.honor

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
class HonorLabels @Inject constructor(
    private val aospLabels: AOSPLabels
) : AutomationLabelSource {

    fun getStorageEntryDynamic(): Set<String>? = aospLabels.getStorageEntryDynamic()

    fun getStorageEntryStatic(lang: String, script: String): Set<String> = when {
        "pl".toLang() == lang -> setOf(
            // HONOR/PGT-N19EEA/HNPGT:13/HONORPGT-N49/7.1.0.176C431E3R2P3:user/release-keys
            "Pamięć",
        )

        else -> emptySet()
    }.tryAppend { aospLabels.getStorageEntryStatic(lang, script) }

    fun getClearCacheDynamic(): Set<String>? = aospLabels.getClearCacheDynamic()

    fun getClearCacheStatic(lang: String, script: String): Set<String> = when {
        else -> emptySet<String>()
    }.tryAppend { aospLabels.getStorageEntryStatic(lang, script) }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Honor", "Labels")
    }
}