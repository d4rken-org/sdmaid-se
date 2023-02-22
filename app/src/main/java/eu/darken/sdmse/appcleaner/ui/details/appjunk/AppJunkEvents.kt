package eu.darken.sdmse.appcleaner.ui.details.appjunk

import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.common.files.core.APath
import kotlin.reflect.KClass

sealed class AppJunkEvents {
    data class ConfirmDeletion(
        val appJunk: AppJunk,
        val filterType: KClass<out ExpendablesFilter>? = null,
        val path: APath? = null,
        val onlyInaccessible: Boolean = false,
    ) : AppJunkEvents()

    data class TaskForParent(val task: AppCleanerTask) : AppJunkEvents()
}
