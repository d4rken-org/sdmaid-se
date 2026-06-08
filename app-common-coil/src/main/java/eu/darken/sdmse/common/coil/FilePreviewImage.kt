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
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.R as CommonR

/**
 * Compose counterpart to the legacy `ImageView.loadFilePreview()` helper.
 *
 * Loads a preview for [lookup] via Coil and falls back to a tonal-surface placeholder (a tinted
 * [Icon] on a [fallbackBackground] box) whenever a real bitmap can't be shown:
 *
 * - Items that can never have a preview (directories, symlinks, unknown types, zero-size files)
 *   short-circuit and render the placeholder directly.
 * - Items that go through Coil reuse the same placeholder for the `loading` and `error` slots, so
 *   binaries Coil can't decode look identical to a directory tile instead of rendering the raw
 *   vector drawable through Coil's pipeline (where `android:tint="?attr/colorControlNormal"` does
 *   not resolve and the icon ends up oversized / untinted on light surfaces).
 */
@Composable
fun FilePreviewImage(
    lookup: APathLookup<*>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    fallbackTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fallbackBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val fallbackRes = when (lookup.fileType) {
        FileType.DIRECTORY -> CommonR.drawable.ic_folder
        FileType.SYMBOLIC_LINK -> CommonR.drawable.ic_file_link
        FileType.FILE -> CommonR.drawable.ic_file
        FileType.UNKNOWN -> CommonR.drawable.file_question
    }

    val fallback: @Composable (Modifier) -> Unit = { boxModifier ->
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

    val canHavePreview = lookup.fileType == FileType.FILE && lookup.size > 0L
    if (!canHavePreview) {
        fallback(modifier)
        return
    }

    val context = LocalContext.current
    // remember the request per-lookup so row recompositions (e.g. a selection toggle elsewhere in
    // the row) don't rebuild it and re-trigger Coil's loading slot, which would flash the thumbnail.
    val request = remember(lookup) {
        ImageRequest.Builder(context)
            .data(lookup)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
        loading = { fallback(Modifier.fillMaxSize()) },
        error = { fallback(Modifier.fillMaxSize()) },
    )
}
