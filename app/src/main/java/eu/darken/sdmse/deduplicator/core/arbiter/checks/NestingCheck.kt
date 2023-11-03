package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class NestingCheck @Inject constructor() : ArbiterCheck {
    suspend fun checkDuplicate(criterium: ArbiterCriterium.Nesting): Comparator<Duplicate> = when (criterium.mode) {
        ArbiterCriterium.Nesting.Mode.PREFER_SHALLOW -> compareBy { it.path.segments.size }
        ArbiterCriterium.Nesting.Mode.PREFER_DEEPER -> compareByDescending { it.path.segments.size }
    }
}