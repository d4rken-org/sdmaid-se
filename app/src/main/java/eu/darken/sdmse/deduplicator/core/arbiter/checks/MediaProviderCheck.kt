package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class MediaProviderCheck @Inject constructor() : ArbiterCheck {
    suspend fun checkDuplicate(criterium: ArbiterCriterium.MediaProvider): Comparator<Duplicate> {
        return Comparator { _, _ -> 0 }
    }
}