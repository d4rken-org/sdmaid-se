package eu.darken.sdmse.corpsefinder.core.tasks

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.parcelize.Parcelize
import java.time.Duration

sealed class CorpseFinderTask : SDMTool.Task {
    override val type: SDMTool.Type = SDMTool.Type.CORPSEFINDER

    sealed class Result : SDMTool.Task.Result
}

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

