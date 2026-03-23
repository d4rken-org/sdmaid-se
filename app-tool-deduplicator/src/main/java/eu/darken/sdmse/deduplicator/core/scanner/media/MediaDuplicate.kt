package eu.darken.sdmse.deduplicator.core.scanner.media

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.media.audiohash.AudioFingerprinter
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher

data class MediaDuplicate(
    override val lookup: APathLookup<*>,
    val audioHash: AudioFingerprinter.Result?,
    val frameHashes: List<PHasher.Result>,
    val similarity: Double,
) : Duplicate {

    override val type: Duplicate.Type
        get() = Duplicate.Type.MEDIA

    data class Group(
        override val identifier: Duplicate.Group.Id,
        override val duplicates: Set<MediaDuplicate>,
        override val keeperIdentifier: Duplicate.Id? = null,
    ) : Duplicate.Group {
        override val type: Duplicate.Type
            get() = Duplicate.Type.MEDIA
    }
}
