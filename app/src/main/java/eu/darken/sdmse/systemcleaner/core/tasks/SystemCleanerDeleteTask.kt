package eu.darken.sdmse.systemcleaner.core.tasks

import eu.darken.sdmse.common.files.core.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class SystemCleanerDeleteTask(
    val toDelete: Set<APath> = emptySet(),
    val isWatcherTask: Boolean = false,
) : SystemCleanerTask() {

    @Parcelize
    data class Success(
        private val resultCount: Int,
    ) : Result()

    @Parcelize
    data class Error(
        val exception: Exception
    ) : Result()
}