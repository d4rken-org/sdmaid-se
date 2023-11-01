package eu.darken.sdmse.deduplicator.core.scanner.phash

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.deduplicator.core.Duplicate

data class PHashDuplicate(
    override val lookup: APathLookup<*>,
    val hash: Hasher.Result,
) : Duplicate {

    data class Group(
        override val identifier: Duplicate.Group.Identifier,
        override val duplicates: Set<PHashDuplicate>
    ) : Duplicate.Group
}