package eu.darken.sdmse.deduplicator.core.deleter

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.deduplicator.core.Duplicate
import javax.inject.Inject

@Reusable
class DupeArbiter @Inject constructor() {


    suspend fun decideGroups(litigants: Collection<Duplicate.Group>): Pair<Duplicate.Group, Collection<Duplicate.Group>> {
        // TODO
        return litigants.first() to litigants.drop(1)
    }

    suspend fun decideDuplicates(litigants: Collection<Duplicate>): Pair<Duplicate, Collection<Duplicate>> {
        // TODO
        return litigants.first() to litigants.drop(1)
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Arbiter")
    }
}