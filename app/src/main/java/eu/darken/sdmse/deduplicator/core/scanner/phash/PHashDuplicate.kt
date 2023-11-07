package eu.darken.sdmse.deduplicator.core.scanner.phash

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.deduplicator.core.Duplicate

data class PHashDuplicate(
    override val lookup: APathLookup<*>,
    val hash: PHash.Result,
) : Duplicate {

    override val type: Duplicate.Type
        get() = Duplicate.Type.PHASH

    data class Group(
        override val identifier: Duplicate.Group.Id,
        override val duplicates: Set<PHashDuplicate>
    ) : Duplicate.Group {
        override val type: Duplicate.Type
            get() = Duplicate.Type.PHASH
    }
}