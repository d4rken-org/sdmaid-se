package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
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

    suspend fun deleteAll(
        targets: Collection<ExpendablesFilter.Match.Deletion>,
        gatewaySwitch: GatewaySwitch,
        allMatches: Collection<ExpendablesFilter.Match>,
    ): ExpendablesFilter.ProcessResult {
        log(TAG, INFO) { "deleteAll(...) Processing ${targets.size} out of ${allMatches.size} matches" }
        val successful = mutableSetOf<ExpendablesFilter.Match>()
        val failed = mutableSetOf<Pair<ExpendablesFilter.Match, Exception>>()

        val distinctRoots = targets.map { it.lookup }.filterDistinctRoots()

        if (distinctRoots.size != targets.size) {
            log(TAG, INFO) { "${targets.size} match objects but only ${distinctRoots.size} distinct roots" }
            if (Bugs.isDebug) {
                targets
                    .filter { !distinctRoots.contains(it.lookup) }
                    .forEachIndexed { index, item -> log(TAG, INFO) { "Non distinct root #$index: $item" } }
            }
        }

        distinctRoots.forEach { targetRoot ->
            updateProgressSecondary(targetRoot.userReadablePath)
            val main = targets.first { it.lookup == targetRoot }

            val mainDeleted = try {
                targetRoot.deleteAll(gatewaySwitch)
                log(TAG) { "Main match deleted: $main" }
                true
            } catch (oge: PathException) {
                try {
                    if (targetRoot.exists(gatewaySwitch)) {
                        log(TAG, WARN) { "Deletion failed, file still exists" }
                        failed.add(main to oge)
                        false
                    } else {
                        log(TAG, WARN) { "Deletion failed as file no longer exists, okay..." }
                        true
                    }
                } catch (e: PathException) {
                    log(TAG, ERROR) { "Post-deletion-failure-exist check failed too on $main\n $e" }
                    failed.add(main to e)
                    false
                }
            }

            // deleteAll(...) starts at leafs, children may have been deleted, even if the top-level dir wasn't
            val affected = allMatches.filter { it != main && main.lookup.isAncestorOf(it.lookup) }
            if (Bugs.isDebug) {
                log(TAG) { "$main affects ${affected.size} other matches" }
                affected.forEach { log(TAG, VERBOSE) { "Affected: $it" } }
            }

            if (mainDeleted) {
                successful.add(main)
                successful.addAll(affected)
                log(TAG) { "Main match and affected files deleted" }
            } else {
                log(TAG, WARN) { "Main match failed to delete, checking what still exists" }
                affected.forEach { subMatch ->
                    if (subMatch.path.exists(gatewaySwitch)) {
                        log(TAG, WARN) { "Sub match still exists: $subMatch" }
                    } else {
                        log(TAG, INFO) { "Sub match no longer exists: $subMatch" }
                        successful.add(subMatch)
                    }
                }
            }
        }

        return ExpendablesFilter.ProcessResult(
            success = successful,
            failed = failed,
        )
    }

    suspend fun APathLookup<*>.toDeletionMatch() = ExpendablesFilter.Match.Deletion(identifier, this)


    companion object {
        private val TAG = logTag("AppCleaner", "BaseExpendablesFilter")
    }
}