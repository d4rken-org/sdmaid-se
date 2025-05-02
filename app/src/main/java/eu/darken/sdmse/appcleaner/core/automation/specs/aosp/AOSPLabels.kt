package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import javax.inject.Inject

@Reusable
class AOSPLabels @Inject constructor(
    private val labels14Plus: AOSPLabels14Plus,
    private val labels29Plus: AOSPLabels29Plus,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getStorageEntryDynamic(acsContext)
        else -> labels14Plus.getStorageEntryDynamic(acsContext)
    }

    fun getStorageEntryStatic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getStorageEntryStatic(acsContext)
        else -> labels14Plus.getStorageEntryStatic(acsContext)
    }

    fun getClearCacheDynamic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getClearCacheDynamic(acsContext)
        else -> labels14Plus.getClearCacheDynamic(acsContext)
    }

    fun getClearCacheStatic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getClearCacheStatic(acsContext)
        else -> labels14Plus.getClearCacheStatic(acsContext)
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "AOSP", "Labels")
    }
}