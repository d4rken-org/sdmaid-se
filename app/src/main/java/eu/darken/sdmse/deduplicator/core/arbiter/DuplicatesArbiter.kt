package eu.darken.sdmse.deduplicator.core.arbiter

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.checks.DuplicateTypeCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.LocationCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.MediaProviderCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.ModificationCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.NestingCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.SizeCheck
import javax.inject.Inject


class DuplicatesArbiter @Inject constructor(
    private val duplicateTypeCheck: DuplicateTypeCheck,
    private val mediaProviderCheck: MediaProviderCheck,
    private val locationCheck: LocationCheck,
    private val nestingCheck: NestingCheck,
    private val modificationCheck: ModificationCheck,
    private val sizeCheck: SizeCheck,
) {

    private val strategy = ArbiterStrategy(
        criteria = listOf(
            ArbiterCriterium.DuplicateType(),
            ArbiterCriterium.MediaProvider(),
            ArbiterCriterium.Location(),
            ArbiterCriterium.Nesting(),
            ArbiterCriterium.Modified(),
        )
    )

    suspend fun decideGroups(litigants: Collection<Duplicate.Group>): Pair<Duplicate.Group, Set<Duplicate.Group>> {
        log(TAG) { "decideGroups(): ${litigants.size} items, strategy=$strategy" }

        if (litigants.isEmpty()) throw IllegalArgumentException("Must pass at least 1 group!")
        if (litigants.any { it.duplicates.isEmpty() }) throw IllegalArgumentException("All groups must be non-empty!")

        var workList = litigants

        strategy.criteria.forEach { crit ->
            val comparator: Comparator<Duplicate.Group> = when (crit) {
                is ArbiterCriterium.DuplicateType -> duplicateTypeCheck.checkGroup(crit)
                is ArbiterCriterium.MediaProvider -> null
                is ArbiterCriterium.Location -> null
                is ArbiterCriterium.Nesting -> null
                is ArbiterCriterium.Modified -> null
                is ArbiterCriterium.Size -> null
            } ?: return@forEach
            workList = workList.sortedWith(comparator)
        }

        return workList.first() to workList.drop(1).toSet()
    }

    suspend fun decideDuplicates(litigants: Collection<Duplicate>): Pair<Duplicate, Collection<Duplicate>> {
        log(TAG) { "decideDuplicates(): ${litigants.size} items, strategy=$strategy" }
        if (litigants.isEmpty()) throw IllegalArgumentException("Must pass at least 1 duplicate!")

        var workList = litigants

        strategy.criteria.forEach { crit ->
            val comparator: Comparator<Duplicate> = when (crit) {
                is ArbiterCriterium.DuplicateType -> duplicateTypeCheck.checkDuplicate(crit)
                is ArbiterCriterium.MediaProvider -> mediaProviderCheck.checkDuplicate(crit) // TODO
                is ArbiterCriterium.Location -> locationCheck.checkDuplicate(crit) // TODO
                is ArbiterCriterium.Nesting -> nestingCheck.checkDuplicate(crit)
                is ArbiterCriterium.Modified -> modificationCheck.checkDuplicate(crit) // TODO
                is ArbiterCriterium.Size -> sizeCheck.checkDuplicate(crit)
            }
            workList = workList.sortedWith(comparator)
        }

        workList.forEachIndexed { index, duplicate ->
            log(TAG, VERBOSE) { "decideDuplicates(): #$index $duplicate" }
        }

        return workList.first() to workList.drop(1)
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Arbiter")
    }
}