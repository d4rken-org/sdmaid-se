package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize
import java.time.Duration

@Parcelize
data class AppControlScanTask(
    val pkgIdFilter: Set<Pkg.Id> = emptySet(),
) : AppControlTask() {

    @Parcelize
    data class Success(
        private val duration: Duration,
    ) : Result()

    @Parcelize
    data class Error(
        val exception: Exception
    ) : Result()
}