package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.PathException
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
    ): ExpendablesFilter.ProcessResult = this
        .map { it as ExpendablesFilter.Match.Deletion }
        .let { deletionMatches ->
            val success = mutableSetOf<ExpendablesFilter.Match.Deletion>()
            val failed = mutableSetOf<Pair<ExpendablesFilter.Match.Deletion, Exception>>()

            val distinctRoots = deletionMatches.map { it.lookup }.filterDistinctRoots()

            if (distinctRoots.size != deletionMatches.size) {
                log(WARN) { "${deletionMatches.size} match objects but only ${distinctRoots.size} distinct roots" }
                if (Bugs.isDebug) {
                    deletionMatches
                        .filter { !distinctRoots.contains(it.lookup) }
                        .forEachIndexed { index, item -> log(WARN) { "Non distinct root #$index: $item" } }
                }
            }

            distinctRoots.forEach { targetContent ->
                updateProgressSecondary(targetContent.userReadablePath)
                val originalmatch = deletionMatches.first { it.lookup == targetContent }
                try {
                    targetContent.deleteAll(gatewaySwitch)
                    success.add(originalmatch)
                } catch (e: PathException) {
                    log(logTag("AppCleaner,BaseExpendablesFilter"), ERROR) {
                        "Failed to delete $originalmatch due to $e"
                    }
                    failed.add(originalmatch to e)
                }
            }

            ExpendablesFilter.ProcessResult(
                success = success,
                failed = failed,
            )
        }

    suspend fun APathLookup<*>.toDeletionMatch() = ExpendablesFilter.Match.Deletion(identifier, this)

}