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
        filePainters = listOf(
            painterResource(R.drawable.ss_photo_beach),
            painterResource(R.drawable.ss_photo_forest),
            painterResource(R.drawable.ss_photo_ocean),
            painterResource(R.drawable.ss_photo_desert),
            painterResource(R.drawable.ss_photo_mountain),
            painterResource(R.drawable.ss_photo_meadow),
        ),
    )
    PreviewWrapper {
        CompositionLocalProvider(LocalPreviewImageProvider provides sampleImages) {
            DeduplicatorListScreen(
                stateSource = MutableStateFlow(
                    DeduplicatorListViewModel.State(
                        rows = listOf(
                            sampleRow("dupe-1", "vacation.jpg", sizeMb = 8, copies = 2),
                            sampleRow("dupe-2", "sunset_beach.jpg", sizeMb = 6, copies = 3),
                            sampleRow("dupe-3", "mountains.png", sizeMb = 12, copies = 2),
                            sampleRow("dupe-4", "birthday.jpg", sizeMb = 3, copies = 4),
                            sampleRow("dupe-5", "concert.jpg", sizeMb = 5, copies = 2),
                            sampleRow("dupe-6", "roadtrip.png", sizeMb = 9, copies = 2),
                            sampleRow("dupe-7", "hiking_trail.jpg", sizeMb = 7, copies = 2),
                            sampleRow("dupe-8", "city_lights.jpg", sizeMb = 4, copies = 3),
                            sampleRow("dupe-9", "forest_walk.png", sizeMb = 11, copies = 2),
                            sampleRow("dupe-10", "old_harbour.jpg", sizeMb = 2, copies = 5),
                            sampleRow("dupe-11", "desert_dunes.jpg", sizeMb = 14, copies = 2),
                            sampleRow("dupe-12", "garden.png", sizeMb = 6, copies = 3),
                        ),
                        layoutMode = LayoutMode.GRID,
                    ),
                ),
            )
        }
    }
}
