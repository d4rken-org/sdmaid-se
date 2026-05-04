package eu.darken.sdmse.deduplicator.ui.preview

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.media.MediaDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import okio.ByteString.Companion.encodeUtf8
import java.time.Instant

private fun previewLookup(
    pathSegments: Array<String>,
    fileType: FileType = FileType.FILE,
    size: Long = 1024L * 1024,
): LocalPathLookup = LocalPathLookup(
    lookedUp = LocalPath.build(*pathSegments),
    fileType = fileType,
    size = size,
    modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
    target = null,
)

internal fun previewChecksumDuplicate(
    pathSegments: Array<String> = arrayOf("storage", "emulated", "0", "Pictures", "vacation.jpg"),
    size: Long = 4L * 1024 * 1024,
    hashSeed: String = "preview-hash",
): ChecksumDuplicate = ChecksumDuplicate(
    lookup = previewLookup(pathSegments = pathSegments, size = size),
    hash = Hasher.Result(type = Hasher.Type.MD5, hash = hashSeed.encodeUtf8()),
)

internal fun previewPHashDuplicate(
    pathSegments: Array<String> = arrayOf("storage", "emulated", "0", "Pictures", "sunset.jpg"),
    size: Long = 3L * 1024 * 1024,
    similarity: Double = 0.96,
): PHashDuplicate = PHashDuplicate(
    lookup = previewLookup(pathSegments = pathSegments, size = size),
    hash = PHasher.Result(),
    similarity = similarity,
)

internal fun previewMediaDuplicate(
    pathSegments: Array<String> = arrayOf("storage", "emulated", "0", "Movies", "trip.mp4"),
    size: Long = 32L * 1024 * 1024,
    similarity: Double = 0.91,
): MediaDuplicate = MediaDuplicate(
    lookup = previewLookup(pathSegments = pathSegments, size = size),
    audioHash = null,
    frameHashes = emptyList(),
    similarity = similarity,
)

internal fun previewChecksumGroup(
    identifier: Duplicate.Group.Id = Duplicate.Group.Id("preview-checksum-1"),
    duplicates: Set<ChecksumDuplicate> = setOf(
        previewChecksumDuplicate(
            pathSegments = arrayOf("storage", "emulated", "0", "Pictures", "vacation.jpg"),
            hashSeed = "vacation-1",
        ),
        previewChecksumDuplicate(
            pathSegments = arrayOf("storage", "emulated", "0", "Pictures", "vacation_copy.jpg"),
            hashSeed = "vacation-1",
        ),
    ),
): ChecksumDuplicate.Group = ChecksumDuplicate.Group(
    duplicates = duplicates,
    identifier = identifier,
)

internal fun previewPHashGroup(
    identifier: Duplicate.Group.Id = Duplicate.Group.Id("preview-phash-1"),
    duplicates: Set<PHashDuplicate> = setOf(
        previewPHashDuplicate(
            pathSegments = arrayOf("storage", "emulated", "0", "Pictures", "sunset.jpg"),
            similarity = 1.0,
        ),
        previewPHashDuplicate(
            pathSegments = arrayOf("storage", "emulated", "0", "Pictures", "sunset_edit.jpg"),
            similarity = 0.93,
        ),
    ),
): PHashDuplicate.Group = PHashDuplicate.Group(
    identifier = identifier,
    duplicates = duplicates,
)

internal fun previewMediaGroup(
    identifier: Duplicate.Group.Id = Duplicate.Group.Id("preview-media-1"),
    duplicates: Set<MediaDuplicate> = setOf(
        previewMediaDuplicate(
            pathSegments = arrayOf("storage", "emulated", "0", "Movies", "trip.mp4"),
            similarity = 1.0,
        ),
        previewMediaDuplicate(
            pathSegments = arrayOf("storage", "emulated", "0", "Movies", "trip_compressed.mp4"),
            similarity = 0.88,
        ),
    ),
): MediaDuplicate.Group = MediaDuplicate.Group(
    identifier = identifier,
    duplicates = duplicates,
)

internal fun previewCluster(
    identifier: Duplicate.Cluster.Id = Duplicate.Cluster.Id("preview-cluster"),
    groups: Set<Duplicate.Group> = setOf(previewChecksumGroup()),
    favoriteGroupIdentifier: Duplicate.Group.Id? = null,
): Duplicate.Cluster = Duplicate.Cluster(
    identifier = identifier,
    groups = groups,
    favoriteGroupIdentifier = favoriteGroupIdentifier,
)
