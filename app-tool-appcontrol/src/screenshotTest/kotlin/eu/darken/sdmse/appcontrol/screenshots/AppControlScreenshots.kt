package eu.darken.sdmse.appcontrol.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.appcontrol.ui.list.AppControlListScreen
import eu.darken.sdmse.appcontrol.ui.list.AppControlListViewModel
import eu.darken.sdmse.appcontrol.ui.preview.previewAppControlRow
import eu.darken.sdmse.appcontrol.ui.preview.previewAppInfo
import eu.darken.sdmse.appcontrol.ui.preview.previewInstalled
import eu.darken.sdmse.appcontrol.ui.preview.previewSizeStats
import eu.darken.sdmse.common.coil.LocalPreviewImageProvider
import eu.darken.sdmse.common.coil.rememberSampleImageProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow

// App icons can't be loaded by Coil under layoutlib, so the render installs sample icons via
// LocalPreviewImageProvider (see AppIconImage), deterministic per package name. Fictional apps only
// (no real names/packages); sizes and active state are varied to look like a real device.

private const val MB = 1024L * 1024L

private fun appRow(label: String, pkg: String, appMb: Long, cacheMb: Long, dataMb: Long, active: Boolean) =
    previewAppControlRow(
        previewAppInfo(
            pkg = previewInstalled(label = label, pkgName = pkg),
            isActive = active,
            sizes = previewSizeStats(appBytes = appMb * MB, cacheBytes = cacheMb * MB, dataBytes = dataMb * MB),
        ),
    )

private val APP_ROWS = listOf(
    appRow("Retro Arcade", "com.pixelforge.retroarcade", 210, 30, 48, active = true),
    appRow("Cloudsync Drive", "com.nimbusworks.clouddrive", 72, 18, 380, active = true),
    appRow("Mosaic Gallery", "com.pixelforge.mosaic", 60, 12, 210, active = false),
    appRow("Snapvault", "com.brightpixel.snapvault", 180, 22, 96, active = true),
    appRow("Trailmark Maps", "com.wanderlab.trailmark", 88, 24, 140, active = true),
    appRow("Beatstream", "com.audionest.beatstream", 96, 40, 60, active = true),
    appRow("Chattr", "com.hollowpine.chattr", 40, 14, 88, active = false),
    appRow("FitPulse", "com.vitalstep.fitpulse", 52, 7, 64, active = true),
    appRow("Voyage", "com.wanderlab.voyage", 44, 8, 30, active = false),
    appRow("Lumen Reader", "com.paperleaf.lumen", 30, 6, 12, active = false),
    appRow("Zephyr Weather", "com.skygaze.zephyr", 24, 5, 8, active = true),
    appRow("Pocket Ledger", "com.finlytic.ledger", 18, 3, 9, active = false),
    appRow("Nimbus Notes", "net.brightpixel.nimbus", 12, 1, 6, active = true),
    appRow("Tasklet", "com.brightpixel.tasklet", 9, 1, 4, active = false),
)

@PreviewTest
@PlayStoreLocales
@Composable
fun AppControlScreenshot() {
    PreviewWrapper {
        CompositionLocalProvider(LocalPreviewImageProvider provides rememberSampleImageProvider()) {
            AppControlListScreen(
                stateSource = MutableStateFlow(
                    AppControlListViewModel.State(rows = APP_ROWS),
                ),
            )
        }
    }
}
