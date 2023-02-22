package eu.darken.sdmse.appcleaner.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.serialization.KClassParcelizer
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlin.reflect.KClass

@Parcelize
@TypeParceler<KClass<out ExpendablesFilter>, KClassParcelizer>()
data class AppCleanerDeleteTask(
    val targetPkgs: Set<Pkg.Id>? = null,
    val targetFilters: Set<KClass<out ExpendablesFilter>>? = null,
    val targetContents: Set<APath>? = null,
    val onlyInaccessible: Boolean = false,
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
                    R.string.general_result_x_space_freed,
                    Formatter.formatShortFileSize(it, recoveredSpace)
                )
            }
    }
}