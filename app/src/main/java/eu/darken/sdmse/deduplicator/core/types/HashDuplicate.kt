package eu.darken.sdmse.deduplicator.core.types

import eu.darken.sdmse.common.files.APathLookup

data class HashDuplicate(
    override val lookup: APathLookup<*>,
) : Duplicate {


    data class Group(
        override val identifier: Duplicate.Group.Identifier,
        override val duplicates: Collection<HashDuplicate>
    ) : Duplicate.Group
}