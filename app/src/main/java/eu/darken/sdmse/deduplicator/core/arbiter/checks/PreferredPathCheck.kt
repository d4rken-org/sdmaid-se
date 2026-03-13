package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.isDescendantOf
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class PreferredPathCheck @Inject constructor() : ArbiterCheck {
    suspend fun favorite(
        before: List<Duplicate>,
        criterium: ArbiterCriterium.PreferredPath,
    ): List<Duplicate> {
        if (criterium.keepPreferPaths.isEmpty()) {
            log(TAG, VERBOSE) { "No keepPreferPaths configured, returning unchanged" }
            return before
        }

        log(TAG) { "keepPreferPaths: ${criterium.keepPreferPaths}" }

        // Files IN configured paths sort first (kept); files NOT in configured paths sort last (deleted)
        val sorted = before.sortedBy { duplicate ->
            val isInKeepPreferPath = criterium.keepPreferPaths.any { preferPath ->
                val isDescendant = duplicate.path.isDescendantOf(preferPath)
                val isEqual = duplicate.path == preferPath
                log(TAG, VERBOSE) {
                    "Check: ${duplicate.path} vs $preferPath -> isDescendant=$isDescendant, isEqual=$isEqual"
                }
                isDescendant || isEqual
            }
            val sortKey = if (isInKeepPreferPath) 0 else 1
            log(TAG, VERBOSE) { "${duplicate.path} -> isInKeepPreferPath=$isInKeepPreferPath, sortKey=$sortKey" }
            sortKey
        }

        log(TAG) { "Before: ${before.map { it.path }}" }
        log(TAG) { "After:  ${sorted.map { it.path }}" }

        return sorted
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Arbiter", "PreferredPathCheck")
    }
}
