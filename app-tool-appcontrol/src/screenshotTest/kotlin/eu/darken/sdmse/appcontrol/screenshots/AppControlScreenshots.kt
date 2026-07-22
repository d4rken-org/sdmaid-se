package eu.darken.sdmse.appcontrol.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.appcontrol.ui.list.AppControlListScreen
import eu.darken.sdmse.appcontrol.ui.list.AppControlListViewModel
import eu.darken.sdmse.appcontrol.ui.preview.previewAppControlRow
import eu.darken.sdmse.appcontrol.ui.preview.previewAppInfo
import eu.darken.sdmse.appcontrol.ui.preview.previewInstalled
import eu.darken.sdmse.common.coil.LocalPreviewImageProvider
import eu.darken.sdmse.common.coil.rememberSampleImageProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow

// App icons can't be loaded by Coil under layoutlib, so the render installs sample icons via
// LocalPreviewImageProvider (see AppIconImage), deterministic per package name.

@PreviewTest
@PlayStoreLocales
@Composable
fun AppControlScreenshot() {
    PreviewWrapper {
        CompositionLocalProvider(LocalPreviewImageProvider provides rememberSampleImageProvider()) {
            AppControlListScreen(
                stateSource = MutableStateFlow(
                    AppControlListViewModel.State(
                        rows = listOf(
                            previewAppControlRow(previewAppInfo(pkg = previewInstalled(label = "Firefox", pkgName = "org.mozilla.firefox"))),
                            previewAppControlRow(previewAppInfo(pkg = previewInstalled(label = "Signal", pkgName = "org.thoughtcrime.securesms"))),
                            previewAppControlRow(previewAppInfo(pkg = previewInstalled(label = "F-Droid", pkgName = "org.fdroid.fdroid"))),
                            previewAppControlRow(previewAppInfo(pkg = previewInstalled(label = "VLC", pkgName = "org.videolan.vlc"))),
                            previewAppControlRow(previewAppInfo(pkg = previewInstalled(label = "Nextcloud", pkgName = "com.nextcloud.client"))),
                        ),
                    ),
                ),
            )
        }
    }
}
