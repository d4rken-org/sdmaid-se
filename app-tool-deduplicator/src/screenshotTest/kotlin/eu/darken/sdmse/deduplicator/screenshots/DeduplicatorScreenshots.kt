package eu.darken.sdmse.deduplicator.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.painterResource
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.common.coil.LocalPreviewImageProvider
import eu.darken.sdmse.common.coil.rememberSampleImageProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.deduplicator.R
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListScreen
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListViewModel
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import kotlinx.coroutines.flow.MutableStateFlow

// File thumbnails can't be loaded by Coil under layoutlib, so the render installs sample images via
// LocalPreviewImageProvider (see FilePreviewImage). Here we feed real photo-like sample images (from
// debug/res, picked deterministically per file path) so the grid reads as duplicate photos; each
// cluster uses a distinct file name to get a distinct sample image and size.

private const val MB = 1024L * 1024L

private fun sampleRow(id: String, fileName: String, sizeMb: Long, copies: Int): DeduplicatorListViewModel.DeduplicatorListRow {
    val base = fileName.substringBeforeLast('.')
    val ext = fileName.substringAfterLast('.', "")
    val size = sizeMb * MB
    val duplicates = (0 until copies).map { i ->
        val name = if (i == 0) fileName else "${base}_copy$i.$ext"
        previewChecksumDuplicate(
            pathSegments = arrayOf("storage", "emulated", "0", "Pictures", name),
            size = size,
            hashSeed = base,
        )
    }.toSet()
    val keeper = duplicates.first()
    val group = ChecksumDuplicate.Group(
        duplicates = duplicates,
        identifier = Duplicate.Group.Id("$id-group"),
        keeperIdentifier = keeper.identifier,
    )
    val cluster = previewCluster(
        identifier = Duplicate.Cluster.Id(id),
        groups = setOf(group),
        favoriteGroupIdentifier = group.identifier,
    )
    val targets = (duplicates - keeper).map { it.identifier }.toSet()
    return DeduplicatorListViewModel.DeduplicatorListRow(
        cluster = cluster,
        deleteTargetIds = targets,
        freeableSize = duplicates.filter { it.identifier in targets }.sumOf { it.size },
    )
}

@PreviewTest
@PlayStoreLocales
@Composable
fun DeduplicatorScreenshot() {
    val sampleImages = rememberSampleImageProvider(
        filePainters = COAST_DRAWABLES.map { painterResource(it) },
    )
    PreviewWrapper {
        CompositionLocalProvider(LocalPreviewImageProvider provides sampleImages) {
            DeduplicatorListScreen(
                stateSource = MutableStateFlow(
                    DeduplicatorListViewModel.State(
                        rows = DUPE_SPECS.mapIndexed { i, (name, sizeMb, copies) ->
                            sampleRow("dupe-$i", name, sizeMb = sizeMb.toLong(), copies = copies)
                        },
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

// (fileName, sizeMb, copies) — distinct file names so the provider picks a spread of sample photos.
private val DUPE_SPECS = listOf(
    Triple("beach_day.jpg", 8, 2), Triple("sunset_bay.jpg", 6, 3), Triple("cliffs.jpg", 12, 2),
    Triple("boardwalk.jpg", 3, 4), Triple("harbour.jpg", 5, 2), Triple("surfers.jpg", 9, 2),
    Triple("tide_pool.jpg", 7, 2), Triple("lighthouse.jpg", 4, 3), Triple("seawall.jpg", 11, 2),
    Triple("dunes.jpg", 2, 5), Triple("pier.jpg", 14, 2), Triple("rockpool.jpg", 6, 3),
    Triple("shoreline.jpg", 10, 2), Triple("coast_road.jpg", 5, 2), Triple("seagulls.jpg", 3, 4),
    Triple("driftwood.jpg", 8, 2), Triple("marina.jpg", 16, 2), Triple("sandbar.jpg", 4, 3),
    Triple("cove.jpg", 7, 2), Triple("breakwater.jpg", 9, 2), Triple("promenade.jpg", 5, 3),
)
