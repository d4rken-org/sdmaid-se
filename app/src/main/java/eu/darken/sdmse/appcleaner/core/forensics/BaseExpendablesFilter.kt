package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.PathException
import eu.darken.sdmse.common.files.deleteAll
import eu.darken.sdmse.common.files.exists
import eu.darken.sdmse.common.files.filterDistinctRoots
import eu.darken.sdmse.common.files.isAncestorOf
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
            val successful = mutableSetOf<ExpendablesFilter.Match.Deletion>()
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

            val workingSet = deletionMatches.toMutableSet()

            distinctRoots.forEach { targetRoot ->
                updateProgressSecondary(targetRoot.userReadablePath)
                val mainmatch = workingSet.first { it.lookup == targetRoot }
                val alsoAffected = workingSet
                    .filter { it != mainmatch && mainmatch.lookup.isAncestorOf(it.lookup) }
                    .toSet()

                val mainDeleted = try {
                    targetRoot.deleteAll(gatewaySwitch)
                    log(TAG, VERBOSE) { "Main match deleted" }
                    true
                } catch (oge: PathException) {
                    try {
                        if (targetRoot.exists(gatewaySwitch)) {
                            log(TAG, WARN) { "Deletion failed, file still exists" }
                            failed.add(mainmatch to oge)
                            false
                        } else {
                            log(TAG, WARN) { "Deletion failed as file no longer exists, okay..." }
                            true
                        }
                    } catch (e: PathException) {
                        log(TAG, ERROR) { "Post-deletion-failure-exist check failed too on $mainmatch due to $e" }
                        failed.add(mainmatch to e)
                        false
                    }
                }

                if (mainDeleted) {
                    workingSet.remove(mainmatch)
                    successful.add(mainmatch)
                    if (Bugs.isDebug) {
                        log(TAG, VERBOSE) { "Main deleted:\n$mainmatch" }
                    }
                }

                if (mainDeleted) {
                    workingSet.removeAll(alsoAffected)
                    successful.addAll(alsoAffected)
                    if (Bugs.isDebug) {
                        log(TAG, VERBOSE) { "Also deleted:\n${alsoAffected.joinToString("\n")}" }
                    }
                }
            }

            ExpendablesFilter.ProcessResult(
                success = successful,
                failed = failed,
            )
        }

    suspend fun APathLookup<*>.toDeletionMatch() = ExpendablesFilter.Match.Deletion(identifier, this)


    companion object {
        private val TAG = logTag("AppCleaner", "BaseExpendablesFilter")
    }
}