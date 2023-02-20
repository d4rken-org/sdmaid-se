package eu.darken.sdmse.corpsefinder.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.core.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class CorpseFinderDeleteTask(
    val targetCorpses: Set<APath>? = null,
    val targetContent: Set<APath>? = null,
    val isWatcherTask: Boolean = false,
) : CorpseFinderTask {

    sealed interface Result : CorpseFinderTask.Result

    @Parcelize
    data class Success(
        private val deletedItems: Int,
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