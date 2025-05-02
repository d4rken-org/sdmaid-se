package eu.darken.sdmse.appcleaner.core.automation.specs.vivo

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import javax.inject.Inject

@Reusable
open class VivoLabels @Inject constructor(
    private val vivoLabels14Plus: VivoLabels14Plus,
    private val vivoLabels29Plus: VivoLabels29Plus,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> vivoLabels29Plus.getStorageEntryDynamic(acsContext)
        else -> vivoLabels14Plus.getStorageEntryDynamic(acsContext)
    }

    fun getStorageEntryStatic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> vivoLabels29Plus.getStorageEntryStatic(acsContext)
        else -> vivoLabels14Plus.getStorageEntryStatic(acsContext)
    }

    fun getClearCacheDynamic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> vivoLabels29Plus.getClearCacheDynamic(acsContext)
        else -> vivoLabels14Plus.getClearCacheDynamic(acsContext)
    }

    fun getClearCacheStatic(acsContext: AutomationExplorer.Context): Set<String> = when {
        hasApiLevel(29) -> vivoLabels29Plus.getClearCacheStatic(acsContext)
        else -> vivoLabels14Plus.getClearCacheStatic(acsContext)
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Vivo", "Labels")
    }
}