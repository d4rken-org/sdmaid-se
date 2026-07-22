package eu.darken.sdmse.common.coil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.pkgs.Pkg

/**
 * Preview/screenshot-only image hook.
 *
 * Coil cannot load app icons ([Pkg]) or on-disk file previews ([APathLookup]) under layoutlib, so
 * `@Preview`/screenshot renders of image-heavy screens would otherwise be blank or fall back to a
 * placeholder. A screenshot-test source set installs a provider (via [LocalPreviewImageProvider]) so
 * those surfaces render real sample bitmaps instead.
 *
 * Production never installs a provider — the local stays `null`, and [FilePreviewImage] / [AppIconImage]
 * behave exactly as before. Both methods return a nullable [Painter]; `null` means "no sample, use the
 * normal fallback", so a provider can opt out per item (e.g. directories) without special-casing.
 */
interface PreviewImageProvider {
    @Composable
    fun fileImage(lookup: APathLookup<*>): Painter?

    @Composable
    fun appIcon(pkg: Pkg): Painter?
}

val LocalPreviewImageProvider = staticCompositionLocalOf<PreviewImageProvider?> { null }
