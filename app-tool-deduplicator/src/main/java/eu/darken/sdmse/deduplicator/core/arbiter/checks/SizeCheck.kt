package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class SizeCheck @Inject constructor() : ArbiterCheck {
    suspend fun favorite(
        before: List<Duplicate>,
        criterium: ArbiterCriterium.Size
    ): List<Duplicate> = when (criterium.mode) {
        ArbiterCriterium.Size.Mode.PREFER_SMALLER -> before.sortedBy { it.size }
        ArbiterCriterium.Size.Mode.PREFER_LARGER -> before.sortedByDescending { it.size }
    }
}