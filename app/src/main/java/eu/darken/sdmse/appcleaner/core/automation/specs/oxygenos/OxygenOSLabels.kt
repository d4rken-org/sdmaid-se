package eu.darken.sdmse.appcleaner.core.automation.specs.oxygenos

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import javax.inject.Inject

@Reusable
open class OxygenOSLabels @Inject constructor(
    private val labels14Plus: OxygenOSLabels14Plus,
    private val labels29Plus: OxygenOSLabels29Plus,
    private val labels31Plus: OxygenOSLabels31Plus,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(31) -> labels31Plus.getStorageEntryDynamic(acsContext)
        hasApiLevel(29) -> labels29Plus.getStorageEntryDynamic(acsContext)
        else -> labels14Plus.getStorageEntryDynamic(acsContext)
    }

    fun getStorageEntryLabels(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(31) -> labels31Plus.getStorageEntryLabels(acsContext)
        hasApiLevel(29) -> labels29Plus.getStorageEntryLabels(acsContext)
        else -> labels14Plus.getStorageEntryLabels(acsContext)
    }

    fun getClearCacheDynamic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(31) -> labels31Plus.getClearCacheDynamic(acsContext)
        hasApiLevel(29) -> labels29Plus.getClearCacheDynamic(acsContext)
        else -> labels14Plus.getClearCacheDynamic(acsContext)
    }

    fun getClearCacheStatic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(31) -> labels31Plus.getClearCacheStatic(acsContext)
        hasApiLevel(29) -> labels29Plus.getClearCacheStatic(acsContext)
        else -> labels14Plus.getClearCacheStatic(acsContext)
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "OnePlus", "Labels")
    }
}