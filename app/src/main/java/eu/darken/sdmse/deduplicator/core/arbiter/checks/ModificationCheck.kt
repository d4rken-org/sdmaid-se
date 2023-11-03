package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class ModificationCheck @Inject constructor() : ArbiterCheck {
    suspend fun checkDuplicate(criterium: ArbiterCriterium.Modified): Comparator<Duplicate> = when (criterium.mode) {
        ArbiterCriterium.Modified.Mode.PREFER_OLDER -> compareBy { it.modifiedAt }
        ArbiterCriterium.Modified.Mode.PREFER_NEWER -> compareByDescending { it.modifiedAt }
    }
}