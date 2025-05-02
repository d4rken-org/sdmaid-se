package eu.darken.sdmse.appcleaner.core.automation.specs.alcatel

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels14Plus
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
class AlcatelLabels @Inject constructor(
    private val labels14Plus: AOSPLabels14Plus,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = labels14Plus.getStorageEntryDynamic(acsContext)

    fun getStorageEntryStatic(
        acsContext: AutomationExplorer.Context
    ) = labels14Plus.getStorageEntryStatic(acsContext)

    fun getClearCacheDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = labels14Plus.getClearCacheDynamic(acsContext)

    fun getClearCacheStatic(
        acsContext: AutomationExplorer.Context
    ): Collection<String> =
        labels14Plus.getClearCacheStatic(acsContext)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Alcatel", "Labels")
    }
}