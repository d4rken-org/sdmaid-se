package eu.darken.sdmse.appcleaner.core.automation.specs.alcatel

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels14Plus
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
class AlcatelLabels @Inject constructor(
    private val labels14Plus: AOSPLabels14Plus,
) : AutomationLabelSource {

    fun getStorageEntryDynamic(): Set<String>? = labels14Plus.getStorageEntryDynamic()

    fun getStorageEntryStatic(lang: String, script: String) = labels14Plus.getStorageEntryStatic(lang, script)

    fun getClearCacheDynamic(): Set<String>? = labels14Plus.getClearCacheDynamic()

    fun getClearCacheStatic(lang: String, script: String): Collection<String> =
        labels14Plus.getClearCacheStatic(lang, script)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Alcatel", "Labels")
    }
}