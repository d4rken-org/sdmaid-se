package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class ModificationCheck @Inject constructor() : ArbiterCheck {
    suspend fun favorite(
        before: List<Duplicate>,
        criterium: ArbiterCriterium.Modified
    ): List<Duplicate> = when (criterium.mode) {
        ArbiterCriterium.Modified.Mode.PREFER_OLDER -> before.sortedBy { it.modifiedAt }
        ArbiterCriterium.Modified.Mode.PREFER_NEWER -> before.sortedByDescending { it.modifiedAt }
    }
}