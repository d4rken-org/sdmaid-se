package eu.darken.sdmse.appcleaner.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.serialization.KClassParcelizer
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlin.reflect.KClass

@Parcelize
@TypeParceler<KClass<out ExpendablesFilter>, KClassParcelizer>()
data class AppCleanerDeleteTask(
    val targetPkgs: Set<Installed.InstallId>? = null,
    val targetFilters: Set<KClass<out ExpendablesFilter>>? = null,
    val targetContents: Set<APath>? = null,
    val includeInaccessible: Boolean = true,
    val onlyInaccessible: Boolean = false,
    val useAutomation: Boolean = true,
) : AppCleanerTask {

    sealed interface Result : AppCleanerTask.Result

    @Parcelize
    data class Success(
        private val deletedCount: Int,
        private val recoveredSpace: Long
    ) : Result {
        override val primaryInfo: CaString
            get() = caString {
                it.getString(
                    eu.darken.sdmse.common.R.string.general_result_x_space_freed,
                    Formatter.formatShortFileSize(it, recoveredSpace)
                )
            }
    }
}