package eu.darken.sdmse.appcleaner.core.automation.specs.oneui

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import javax.inject.Inject

@Reusable
open class OneUILabels @Inject constructor(
    private val labels14Plus: OneUILabels14Plus,
    private val labels29Plus: OneUILabels29Plus,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getStorageEntryDynamic(acsContext)
        else -> labels14Plus.getStorageEntryDynamic(acsContext)
    }

    fun getStorageEntryLabels(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getStorageEntryLabels(acsContext)
        else -> labels14Plus.getStorageEntryLabels(acsContext)
    }

    fun getClearCacheDynamic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getClearCacheDynamic(acsContext)
        else -> labels14Plus.getClearCacheDynamic(acsContext)
    }

    fun getClearCacheLabels(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> labels29Plus.getClearCacheLabels(acsContext)
        else -> labels14Plus.getClearCacheLabels(acsContext)
    }

    companion object {
        private val TAG: String = logTag("AppCleaner", "Automation", "Samsung", "Labels")
    }
}