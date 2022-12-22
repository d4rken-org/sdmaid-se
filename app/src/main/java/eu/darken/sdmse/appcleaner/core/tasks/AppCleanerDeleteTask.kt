package eu.darken.sdmse.appcleaner.core.tasks

import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppCleanerDeleteTask(
    val pkgids: Set<Pkg.Id> = emptySet(),
) : AppCleanerTask() {

    @Parcelize
    data class Success(
        private val resultCount: Int,
    ) : Result()

    @Parcelize
    data class Error(
        val exception: Exception
    ) : Result()
}