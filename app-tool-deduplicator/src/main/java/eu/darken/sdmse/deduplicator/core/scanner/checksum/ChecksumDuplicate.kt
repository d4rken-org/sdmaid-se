package eu.darken.sdmse.deduplicator.core.scanner.checksum

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.deduplicator.core.Duplicate

data class ChecksumDuplicate(
    override val lookup: APathLookup<*>,
    val hash: Hasher.Result,
) : Duplicate {

    override val type: Duplicate.Type
        get() = Duplicate.Type.CHECKSUM

    data class Group(
        override val duplicates: Set<ChecksumDuplicate>,
        override val identifier: Duplicate.Group.Id,
        override val keeperIdentifier: Duplicate.Id? = null,
    ) : Duplicate.Group {

        override val type: Duplicate.Type
            get() = Duplicate.Type.CHECKSUM

        val preview: APathLookup<*>
            get() = duplicates.first().lookup
    }
}