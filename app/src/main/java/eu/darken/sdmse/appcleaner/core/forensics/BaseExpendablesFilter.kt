package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.deleteAll
import eu.darken.sdmse.common.files.filterDistinctRoots
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressSecondary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

abstract class BaseExpendablesFilter : ExpendablesFilter {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun Collection<ExpendablesFilter.Match>.deleteAll(
        gatewaySwitch: GatewaySwitch
    ) = this
        .map { it as ExpendablesFilter.Match.Deletion }
        .map { it.lookup }
        .filterDistinctRoots()
        .forEach { targetContent ->
            updateProgressSecondary(targetContent.userReadablePath)
            targetContent.deleteAll(gatewaySwitch)
        }

    suspend fun APathLookup<*>.toDeletionMatch() = ExpendablesFilter.Match.Deletion(identifier, this)

}