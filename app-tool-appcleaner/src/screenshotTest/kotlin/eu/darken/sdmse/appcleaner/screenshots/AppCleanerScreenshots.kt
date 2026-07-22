package eu.darken.sdmse.appcleaner.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.appcleaner.ui.list.AppCleanerListScreen
import eu.darken.sdmse.appcleaner.ui.list.AppCleanerListViewModel
import eu.darken.sdmse.appcleaner.ui.preview.previewAppCleanerRow
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.coil.LocalPreviewImageProvider
import eu.darken.sdmse.common.coil.rememberSampleImageProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow

// App icons can't be loaded by Coil under layoutlib, so the render installs sample icons via
// LocalPreviewImageProvider (see AppIconImage), deterministic per package name.

@PreviewTest
@PlayStoreLocales
@Composable
fun AppCleanerScreenshot() {
    PreviewWrapper {
        CompositionLocalProvider(LocalPreviewImageProvider provides rememberSampleImageProvider()) {
            val rows = listOf(
                previewAppCleanerRow(previewAppJunk(pkg = previewInstalled(pkgName = "org.mozilla.firefox", label = "Firefox"))),
                previewAppCleanerRow(previewAppJunk(pkg = previewInstalled(pkgName = "com.spotify.music", label = "Spotify"))),
                previewAppCleanerRow(previewAppJunk(pkg = previewInstalled(pkgName = "com.instagram.android", label = "Instagram"))),
                previewAppCleanerRow(previewAppJunk(pkg = previewInstalled(pkgName = "org.telegram.messenger", label = "Telegram"))),
                previewAppCleanerRow(previewAppJunk(pkg = previewInstalled(pkgName = "com.whatsapp", label = "WhatsApp"))),
            )
            AppCleanerListScreen(
                stateSource = MutableStateFlow(
                    AppCleanerListViewModel.State(
                        rows = rows,
                        totalCount = rows.size,
                    ),
                ),
            )
        }
    }
}
