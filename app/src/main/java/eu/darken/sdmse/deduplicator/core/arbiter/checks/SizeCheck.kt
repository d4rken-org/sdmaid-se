package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class SizeCheck @Inject constructor() : ArbiterCheck {
    suspend fun checkDuplicate(criterium: ArbiterCriterium.Size): Comparator<Duplicate> = when (criterium.mode) {
        ArbiterCriterium.Size.Mode.PREFER_LARGER -> compareByDescending { it.size }
        ArbiterCriterium.Size.Mode.PREFER_SMALLER -> compareBy { it.size }
    }
}