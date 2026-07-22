package eu.darken.sdmse.deduplicator.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.common.coil.LocalPreviewImageProvider
import eu.darken.sdmse.common.coil.PreviewImageProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.deduplicator.R
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.media.MediaDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListScreen
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListViewModel
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListViewModel.DeduplicatorListRow
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import eu.darken.sdmse.deduplicator.ui.preview.previewMediaDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewPHashDuplicate
import kotlinx.coroutines.flow.MutableStateFlow

// The Deduplicator matches several ways and on several kinds of file. This screenshot spans all of it
// so the per-tile MatchTypeIcons badge varies: CHECKSUM (exact bytes), PHASH (visually similar images)
// and MEDIA (similar video/audio). The sample thumbnail is chosen by the file extension — real coastal
// photos for images, a play-badge frame for video, album art for audio, and the file-type fallback icon
// for documents/archives (all from debug/res, debug builds only).

private const val MB = 1024L * 1024L

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "gif")
private val VIDEO_EXT = setOf("mp4", "mkv", "mov", "avi", "webm")
private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "wav", "ogg", "aac")

private class DedupSampleProvider(
    private val photos: List<Painter>,
    private val videos: List<Painter>,
    private val audio: List<Painter>,
) : PreviewImageProvider {
    @Composable
    override fun fileImage(lookup: APathLookup<*>): Painter? {
        val pool = when (lookup.path.substringAfterLast('.', "").lowercase()) {
            in IMAGE_EXT -> photos
            in VIDEO_EXT -> videos
            in AUDIO_EXT -> audio
            else -> return null // documents / archives -> file-type fallback icon
        }
        return pool[Math.floorMod(lookup.path.hashCode(), pool.size)]
    }

    @Composable
    override fun appIcon(pkg: Pkg): Painter? = null // not used on this screen
}

private fun copies(name: String, n: Int): List<String> {
    val base = name.substringBeforeLast('.')
    val ext = name.substringAfterLast('.')
    return (0 until n).map { i -> if (i == 0) name else "${base}_$i.$ext" }
}

private fun segs(dir: String, name: String) = arrayOf("storage", "emulated", "0", dir, name)

private fun checksumGroup(gid: String, dir: String, name: String, sizeMb: Int, n: Int): ChecksumDuplicate.Group {
    val dupes = copies(name, n)
        .map { previewChecksumDuplicate(pathSegments = segs(dir, it), size = sizeMb.toLong() * MB, hashSeed = name) }
        .toSet()
    return ChecksumDuplicate.Group(duplicates = dupes, identifier = Duplicate.Group.Id(gid), keeperIdentifier = dupes.first().identifier)
}

private fun phashGroup(gid: String, name: String, sizeMb: Int, n: Int): PHashDuplicate.Group {
    val dupes = copies(name, n)
        .mapIndexed { i, fn -> previewPHashDuplicate(pathSegments = segs("Pictures", fn), size = sizeMb.toLong() * MB, similarity = if (i == 0) 1.0 else 0.9 - i * 0.03) }
        .toSet()
    return PHashDuplicate.Group(identifier = Duplicate.Group.Id(gid), duplicates = dupes, keeperIdentifier = dupes.first().identifier)
}

private fun mediaGroup(gid: String, dir: String, name: String, sizeMb: Int, n: Int): MediaDuplicate.Group {
    val dupes = copies(name, n)
        .mapIndexed { i, fn -> previewMediaDuplicate(pathSegments = segs(dir, fn), size = sizeMb.toLong() * MB, similarity = if (i == 0) 1.0 else 0.9 - i * 0.04) }
        .toSet()
    return MediaDuplicate.Group(identifier = Duplicate.Group.Id(gid), duplicates = dupes, keeperIdentifier = dupes.first().identifier)
}

private fun row(cid: String, vararg groups: Duplicate.Group): DeduplicatorListRow {
    val cluster = previewCluster(Duplicate.Cluster.Id(cid), groups.toSet(), favoriteGroupIdentifier = groups.first().identifier)
    val keeper = groups.first().duplicates.first().identifier
    val all = groups.flatMap { it.duplicates }
    val targets = all.map { it.identifier }.toSet() - keeper
    return DeduplicatorListRow(
        cluster = cluster,
        deleteTargetIds = targets,
        freeableSize = all.filter { it.identifier in targets }.sumOf { it.size },
    )
}

private val DEDUP_ROWS = listOf(
    // Exact byte-for-byte (CHECKSUM) matches across different file kinds.
    row("c-beach", checksumGroup("c-beach", "DCIM", "beach.jpg", 8, 2)),
    row("c-clip", checksumGroup("c-clip", "Movies", "holiday_clip.mp4", 64, 2)),
    row("c-podcast", checksumGroup("c-podcast", "Music", "podcast_ep.mp3", 44, 2)),
    row("c-invoice", checksumGroup("c-invoice", "Documents", "invoice.pdf", 3, 3)),
    row("c-backup", checksumGroup("c-backup", "Download", "backup.zip", 120, 2)),
    row("c-logo", checksumGroup("c-logo", "Pictures", "logo.png", 2, 4)),
    row("c-wedding", checksumGroup("c-wedding", "DCIM", "wedding.jpg", 15, 2)),
    // Visually similar images (PHASH).
    row("p-sunset", phashGroup("p-sunset", "sunset.jpg", 6, 3)),
    row("p-portrait", phashGroup("p-portrait", "portrait.jpg", 4, 2)),
    row("p-skyline", phashGroup("p-skyline", "skyline.jpg", 9, 2)),
    row("p-garden", phashGroup("p-garden", "garden.jpg", 5, 3)),
    row("p-street", phashGroup("p-street", "street.jpg", 7, 2)),
    row("p-hiking", phashGroup("p-hiking", "hiking.jpg", 6, 2)),
    // Similar video / audio (MEDIA).
    row("m-trip", mediaGroup("m-trip", "Movies", "trip.mp4", 96, 2)),
    row("m-concert", mediaGroup("m-concert", "Movies", "concert.mkv", 140, 2)),
    row("m-liveset", mediaGroup("m-liveset", "Music", "live_set.flac", 38, 2)),
    row("m-drone", mediaGroup("m-drone", "Movies", "drone.mov", 210, 2)),
    row("m-track", mediaGroup("m-track", "Music", "album_track.m4a", 12, 3)),
    // Clusters matched two ways at once (CHECKSUM + PHASH badges together).
    row("x-cover", checksumGroup("x-cover-c", "DCIM", "cover.jpg", 5, 2), phashGroup("x-cover-p", "cover_edit.jpg", 5, 2)),
    row("x-poster", checksumGroup("x-poster-c", "Pictures", "poster.png", 8, 2), phashGroup("x-poster-p", "poster_v2.png", 8, 2)),
    row("m-holiday", mediaGroup("m-holiday", "Movies", "holiday.mp4", 72, 2)),
)

@PreviewTest
@PlayStoreLocales
@Composable
fun DeduplicatorScreenshot() {
    val provider = DedupSampleProvider(
        photos = COAST_DRAWABLES.map { painterResource(it) },
        videos = VIDEO_DRAWABLES.map { painterResource(it) },
        audio = AUDIO_DRAWABLES.map { painterResource(it) },
    )
    PreviewWrapper {
        CompositionLocalProvider(LocalPreviewImageProvider provides provider) {
            DeduplicatorListScreen(
                stateSource = MutableStateFlow(
                    DeduplicatorListViewModel.State(
                        rows = DEDUP_ROWS,
                        layoutMode = LayoutMode.GRID,
                    ),
                ),
            )
        }
    }
}

private val COAST_DRAWABLES = listOf(
    R.drawable.ss_coast_00, R.drawable.ss_coast_01, R.drawable.ss_coast_02, R.drawable.ss_coast_03,
    R.drawable.ss_coast_04, R.drawable.ss_coast_05, R.drawable.ss_coast_06, R.drawable.ss_coast_07,
    R.drawable.ss_coast_08, R.drawable.ss_coast_09,
)
private val VIDEO_DRAWABLES = listOf(R.drawable.ss_video_00, R.drawable.ss_video_01)
private val AUDIO_DRAWABLES = listOf(R.drawable.ss_audio_00, R.drawable.ss_audio_01)
