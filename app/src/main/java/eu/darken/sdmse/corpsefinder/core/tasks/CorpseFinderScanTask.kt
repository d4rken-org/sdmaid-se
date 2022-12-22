package eu.darken.sdmse.corpsefinder.core.tasks

import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize
import java.time.Duration

@Parcelize
data class CorpseFinderScanTask(
    val pkgIdFilter: Set<Pkg.Id> = emptySet(),
    val isWatcherTask: Boolean = false,
) : CorpseFinderTask() {

    @Parcelize
    data class Success(
        private val duration: Duration,
    ) : Result()

    @Parcelize
    data class Error(
        val exception: Exception
    ) : Result()
}