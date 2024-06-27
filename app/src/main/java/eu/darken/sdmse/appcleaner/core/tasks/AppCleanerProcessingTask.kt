package eu.darken.sdmse.appcleaner.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.serialization.KClassParcelizer
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlin.reflect.KClass

@Parcelize
@TypeParceler<KClass<out ExpendablesFilter>, KClassParcelizer>()
data class AppCleanerProcessingTask(
    val targetPkgs: Set<Installed.InstallId>? = null,
    val targetFilters: Set<KClass<out ExpendablesFilter>>? = null,
    val targetContents: Set<APath>? = null,
    val includeInaccessible: Boolean = true,
    val onlyInaccessible: Boolean = false,
    val useAutomation: Boolean = true,
    val isBackground: Boolean = false,
) : AppCleanerTask, Reportable {

    sealed interface Result : AppCleanerTask.Result

    @Parcelize
    data class Success(
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>,
    ) : Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.appcleaner_result_x_items_deleted, affectedCount)
            }

        override val secondaryInfo
            get() = caString {
                getString(
                    eu.darken.sdmse.common.R.string.general_result_x_space_freed,
                    Formatter.formatFileSize(this, affectedSpace)
                )
            }
    }
}