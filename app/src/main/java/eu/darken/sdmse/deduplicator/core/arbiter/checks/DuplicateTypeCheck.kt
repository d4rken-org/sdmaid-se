package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class DuplicateTypeCheck @Inject constructor() : ArbiterCheck {

    suspend fun checkGroup(
        criterium: ArbiterCriterium.DuplicateType
    ): Comparator<Duplicate.Group> = when (criterium.mode) {
        ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM -> compareBy { it.type.toPriority() }
        ArbiterCriterium.DuplicateType.Mode.PREFER_PHASH -> compareByDescending { it.type.toPriority() }
    }

    suspend fun checkDuplicate(
        criterium: ArbiterCriterium.DuplicateType
    ): Comparator<Duplicate> = when (criterium.mode) {
        ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM -> compareBy { it.type.toPriority() }
        ArbiterCriterium.DuplicateType.Mode.PREFER_PHASH -> compareByDescending { it.type.toPriority() }
    }

    // Smaller number is better
    fun Duplicate.Type.toPriority() = when (this) {
        Duplicate.Type.CHECKSUM -> 1
        Duplicate.Type.PHASH -> 2
    }
}