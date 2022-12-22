package eu.darken.sdmse.systemcleaner.core.tasks

import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize
import java.time.Duration

@Parcelize
data class SystemCleanerScanTask(
    val pkgIdFilter: Set<Pkg.Id> = emptySet(),
    val isWatcherTask: Boolean = false,
) : SystemCleanerTask() {

    @Parcelize
    data class Success(
        private val duration: Duration,
    ) : Result()

    @Parcelize
    data class Error(
        val exception: Exception
    ) : Result()
}