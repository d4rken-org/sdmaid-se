package eu.darken.sdmse.deduplicator.core.arbiter

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.checks.DuplicateTypeCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.LocationCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.MediaProviderCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.ModificationCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.NestingCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.PreferredPathCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.SizeCheck
import kotlinx.coroutines.flow.first
import javax.inject.Inject


class DuplicatesArbiter @Inject constructor(
    private val settings: DeduplicatorSettings,
    private val duplicateTypeCheck: DuplicateTypeCheck,
    private val mediaProviderCheck: MediaProviderCheck,
    private val locationCheck: LocationCheck,
    private val nestingCheck: NestingCheck,
    private val modificationCheck: ModificationCheck,
    private val sizeCheck: SizeCheck,
    private val preferredPathCheck: PreferredPathCheck,
) {

    suspend fun getStrategy(): ArbiterStrategy {
        val keepPreferPaths = settings.keepPreferPaths.flow.first().paths
        return ArbiterStrategy(
            criteria = listOf(
                ArbiterCriterium.DuplicateType(),
                ArbiterCriterium.MediaProvider(),
                ArbiterCriterium.Location(),
                ArbiterCriterium.Nesting(),
                ArbiterCriterium.Modified(),
                ArbiterCriterium.PreferredPath(keepPreferPaths),
            )
        )
    }

    suspend fun decideGroups(
        litigants: Collection<Duplicate.Group>,
        strategy: ArbiterStrategy,
    ): Pair<Duplicate.Group, Set<Duplicate.Group>> {
        log(TAG) { "decideGroups(): ${litigants.size} items, strategy=$strategy" }

        if (litigants.isEmpty()) throw IllegalArgumentException("Must pass at least 1 group!")
        if (litigants.any { it.duplicates.isEmpty() }) throw IllegalArgumentException("All groups must be non-empty!")

        var workList = litigants.toList()

        strategy.criteria.forEach { crit ->
            val favoritisedWorkList = when (crit) {
                is ArbiterCriterium.DuplicateType -> duplicateTypeCheck.favoriteGroups(workList, crit)
                is ArbiterCriterium.PreferredPath -> null
                is ArbiterCriterium.MediaProvider -> null
                is ArbiterCriterium.Location -> null
                is ArbiterCriterium.Nesting -> null
                is ArbiterCriterium.Modified -> null
                is ArbiterCriterium.Size -> null
            } ?: return@forEach
            workList = favoritisedWorkList
        }

        return workList.first() to workList.drop(1).toSet()
    }

    suspend fun decideDuplicates(
        litigants: Collection<Duplicate>,
        strategy: ArbiterStrategy,
    ): Pair<Duplicate, Collection<Duplicate>> {
        log(TAG) { "decideDuplicates(): ${litigants.size} items, strategy=$strategy" }
        if (litigants.isEmpty()) throw IllegalArgumentException("Must pass at least 1 duplicate!")

        var workList = litigants.toList()

        strategy.criteria.forEach { crit ->
            val favoritisedWorkList = when (crit) {
                is ArbiterCriterium.DuplicateType -> duplicateTypeCheck.favorite(workList, crit)
                is ArbiterCriterium.PreferredPath -> preferredPathCheck.favorite(workList, crit)
                is ArbiterCriterium.MediaProvider -> mediaProviderCheck.favorite(workList, crit)
                is ArbiterCriterium.Location -> locationCheck.favorite(workList, crit)
                is ArbiterCriterium.Nesting -> nestingCheck.favorite(workList, crit)
                is ArbiterCriterium.Modified -> modificationCheck.favorite(workList, crit)
                is ArbiterCriterium.Size -> sizeCheck.favorite(workList, crit)
            }
            workList = favoritisedWorkList
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