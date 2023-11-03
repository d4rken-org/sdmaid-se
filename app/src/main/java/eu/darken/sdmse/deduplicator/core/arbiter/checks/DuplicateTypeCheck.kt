package eu.darken.sdmse.deduplicator.core.arbiter.checks

import dagger.Reusable
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCheck
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import javax.inject.Inject

@Reusable
class DuplicateTypeCheck @Inject constructor() : ArbiterCheck {

    suspend fun favorite(
        before: List<Duplicate>,
        criterium: ArbiterCriterium.DuplicateType,
    ): List<Duplicate> = when (criterium.mode) {
        ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM -> before.sortedBy { it.type.toPriority() }
        ArbiterCriterium.DuplicateType.Mode.PREFER_PHASH -> before.sortedByDescending { it.type.toPriority() }
    }

    suspend fun favoriteGroups(
        before: List<Duplicate.Group>,
        criterium: ArbiterCriterium.DuplicateType
    ): List<Duplicate.Group> = when (criterium.mode) {
        ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM -> before.sortedBy { it.type.toPriority() }
        ArbiterCriterium.DuplicateType.Mode.PREFER_PHASH -> before.sortedByDescending { it.type.toPriority() }
    }

    // Smaller number is better
    fun Duplicate.Type.toPriority() = when (this) {
        Duplicate.Type.CHECKSUM -> 1
        Duplicate.Type.PHASH -> 2
    }
}