package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class LocationCheck @Inject constructor() : ArbiterCheck {
    suspend fun checkDuplicate(criterium: ArbiterCriterium.Location): Comparator<Duplicate> {
        return Comparator { _, _ -> 0 }
    }
}