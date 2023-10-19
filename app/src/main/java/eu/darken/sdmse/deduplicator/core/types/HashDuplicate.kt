package eu.darken.sdmse.deduplicator.core.types

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.hashing.Hasher

data class HashDuplicate(
    override val lookup: APathLookup<*>,
    val hash: Hasher.Result,
) : Duplicate {

    data class Group(
        override val identifier: Duplicate.Group.Identifier,
        override val duplicates: Collection<HashDuplicate>
    ) : Duplicate.Group
}