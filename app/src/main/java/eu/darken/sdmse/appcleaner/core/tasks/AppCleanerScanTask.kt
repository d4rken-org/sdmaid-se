package eu.darken.sdmse.appcleaner.core.tasks

import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize
import java.time.Duration

@Parcelize
data class AppCleanerScanTask(
    val pkgIdFilter: Set<Pkg.Id> = emptySet(),
) : AppCleanerTask() {

    @Parcelize
    data class Success(
        private val duration: Duration,
    ) : Result()

    @Parcelize
    data class Error(
        val exception: Exception
    ) : Result()
}