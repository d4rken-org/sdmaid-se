package eu.darken.sdmse.corpsefinder.core.tasks

import eu.darken.sdmse.common.files.core.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class CorpseFinderDeleteTask(
    val toDelete: Set<APath> = emptySet(),
    val isWatcherTask: Boolean = false,
) : CorpseFinderTask() {

    @Parcelize
    data class Success(
        private val resultCount: Int,
    ) : Result()

    @Parcelize
    data class Error(
        val exception: Exception
    ) : Result()
}