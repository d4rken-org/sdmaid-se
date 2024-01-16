package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.deleteAll
import eu.darken.sdmse.common.files.filterDistinctRoots
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

abstract class BaseSystemCleanerFilter : SystemCleanerFilter {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun Collection<SystemCleanerFilter.Match>.deleteAll(
        gatewaySwitch: GatewaySwitch
    ) = this
        .map { it as SystemCleanerFilter.Match.Deletion }
        .map { it.lookup }
        .filterDistinctRoots()
        .also { updateProgressCount(Progress.Count.Percent(it.size)) }
        .forEach { targetContent ->
            updateProgressPrimary(targetContent.userReadablePath)
            targetContent.deleteAll(gatewaySwitch)
            increaseProgress()
        }
}