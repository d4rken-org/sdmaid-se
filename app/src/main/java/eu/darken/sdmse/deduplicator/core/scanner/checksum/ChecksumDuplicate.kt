package eu.darken.sdmse.deduplicator.core.scanner.checksum

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.deduplicator.core.Duplicate

data class ChecksumDuplicate(
    override val lookup: APathLookup<*>,
    val hash: Hasher.Result,
) : Duplicate {

    data class Group(
        override val identifier: Duplicate.Group.Identifier,
        override val duplicates: Collection<ChecksumDuplicate>
    ) : Duplicate.Group {

        val preview: APathLookup<*>
            get() = duplicates.first().lookup
    }
}