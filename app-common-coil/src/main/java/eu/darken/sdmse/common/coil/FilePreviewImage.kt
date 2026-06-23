package eu.darken.sdmse.common.coil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.R as CommonR

/**
 * Whether a Coil preview can be *attempted* for [this] item — i.e. a non-empty file. A real bitmap is
 * still not guaranteed (unsupported types / disabled previews fall back to a type icon).
 */
fun APathLookup<*>.canAttemptFilePreview(): Boolean = fileType == FileType.FILE && size > 0L

/** Visual used by [FilePreviewImage] when no real preview bitmap can be shown. */
enum class FilePreviewFallback {
    /** Tinted glyph centered on a tonal [MaterialTheme.colorScheme.surfaceVariant] square — for large square preview tiles. */
    Tile,

    /** Full-size tinted [FileType] icon on a transparent background — for list-row leading icons. */
    ListIcon,
}

/**
 * Compose counterpart to the legacy `ImageView.loadFilePreview()` helper.
 *
 * Loads a preview for [lookup] via Coil; when a real bitmap can't be shown it renders a placeholder
 * whose style is chosen by [fallback]:
 *
 * - [FilePreviewFallback.Tile] (default): a tinted glyph on a tonal square — the look wanted by large
 *   square preview surfaces (grid tiles, swipe cards, dedup clusters).
 * - [FilePreviewFallback.ListIcon]: a full-size tinted type icon on a transparent background — the look
 *   wanted by list-row leading icons. Use [FileListThumbnail] instead of passing this directly.
 *
 * In `ListIcon` mode the request also opts out of [PathPreviewFetcher]'s drawable fallback
 * ([PathPreviewFetcher.PARAM_NO_DRAWABLE_FALLBACK]) so files Coil can't decode (unsupported types,
 * previews disabled, decode errors) hit the error slot and render the transparent type icon too,
 * instead of the raw — and in dark mode untinted — vector from Coil's drawable pipeline.
 */
@Composable
fun FilePreviewImage(
    lookup: APathLookup<*>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    fallback: FilePreviewFallback = FilePreviewFallback.Tile,
    fallbackTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fallbackBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    // When false, the Tile loading slot stays transparent instead of showing the tonal placeholder.
    // Use for surfaces that re-mount the same (already memory-cached) image — e.g. the swipe deck,
    // where promoting the next card would otherwise flash the placeholder for a frame before the
    // cached bitmap resolves. ListIcon is always transparent regardless.
    placeholderWhileLoading: Boolean = true,
) {
    // The call-site modifier carries the size and any clip/click; it stays on the container Box so it
    // never lands on the Icon itself.
    val fallbackContent: @Composable (Modifier) -> Unit = { boxModifier ->
        when (fallback) {
            FilePreviewFallback.Tile -> {
                val fallbackRes = when (lookup.fileType) {
                    FileType.DIRECTORY -> CommonR.drawable.ic_folder
                    FileType.SYMBOLIC_LINK -> CommonR.drawable.ic_file_link
                    FileType.FILE -> CommonR.drawable.ic_file
                    FileType.UNKNOWN -> CommonR.drawable.file_question
                }
                Box(
                    modifier = boxModifier.background(fallbackBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(fallbackRes),
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(0.5f),
                        tint = fallbackTint,
                    )
                }
            }

            FilePreviewFallback.ListIcon -> Box(
                modifier = boxModifier,
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = lookup.fileType.icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    tint = fallbackTint,
                )
            }
        }
    }

    if (!lookup.canAttemptFilePreview()) {
        fallbackContent(modifier)
        return
    }

    val context = LocalContext.current
    // remember the request per-lookup so row recompositions (e.g. a selection toggle elsewhere in
    // the row) don't rebuild it and re-trigger Coil's loading slot, which would flash the thumbnail.
    val request = remember(lookup, fallback) {
        ImageRequest.Builder(context)
            .data(lookup)
            .apply {
                if (fallback == FilePreviewFallback.ListIcon) {
                    setParameter(PathPreviewFetcher.PARAM_NO_DRAWABLE_FALLBACK, true)
                }
            }
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
        loading = {
            // Tile shows the tonal placeholder while loading (unless opted out); ListIcon stays
            // transparent to avoid a type-icon flash before the real bitmap resolves.
            val showPlaceholder = placeholderWhileLoading && fallback == FilePreviewFallback.Tile
            if (showPlaceholder) fallbackContent(Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize())
        },
        error = { fallbackContent(Modifier.fillMaxSize()) },
    )
}

/**
 * List-row leading thumbnail: a real Coil preview when one exists, otherwise a full-size tinted
 * [FileType] icon on a transparent background (never the tonal [FilePreviewFallback.Tile] square).
 *
 * Use [FilePreviewImage] directly with the default [FilePreviewFallback.Tile] for large square
 * preview surfaces (grid tiles, swipe cards, dedup cluster previews).
 */
@Composable
fun FileListThumbnail(
    lookup: APathLookup<*>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) = FilePreviewImage(
    lookup = lookup,
    modifier = modifier,
    contentDescription = contentDescription,
    contentScale = contentScale,
    fallback = FilePreviewFallback.ListIcon,
    fallbackTint = iconTint,
)
