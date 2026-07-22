package eu.darken.sdmse.appcleaner.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.appcleaner.ui.list.AppCleanerListScreen
import eu.darken.sdmse.appcleaner.ui.list.AppCleanerListViewModel
import eu.darken.sdmse.appcleaner.ui.preview.previewAppCleanerRow
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInaccessibleCache
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.coil.LocalPreviewImageProvider
import eu.darken.sdmse.common.coil.rememberSampleImageProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow

// App icons can't be loaded by Coil under layoutlib, so the render installs sample icons via
// LocalPreviewImageProvider (see AppIconImage), deterministic per package name. Fictional apps only
// (no real names/packages); sizes and item counts are varied to look like a real cache scan.

private const val KB = 1024L
private const val MB = 1024L * KB

private fun appRow(label: String, pkg: String, size: Long, items: Int) = previewAppCleanerRow(
    previewAppJunk(
        pkg = previewInstalled(label = label, pkgName = pkg),
        expendables = null,
        inaccessibleCache = previewInaccessibleCache(pkgName = pkg, itemCount = items, totalSize = size),
    ),
)

private val APP_ROWS = listOf(
    appRow("Snapvault", "com.brightpixel.snapvault", 214 * MB, 47),
    appRow("Beatstream", "com.audionest.beatstream", 156 * MB, 33),
    appRow("Mosaic Gallery", "com.pixelforge.mosaic", 98 * MB, 21),
    appRow("Voyage", "com.wanderlab.voyage", 72 * MB, 12),
    appRow("Cloudsync Drive", "com.nimbusworks.clouddrive", 64 * MB, 18),
    appRow("Lumen Reader", "com.paperleaf.lumen", 41 * MB, 9),
    appRow("Retro Arcade", "com.pixelforge.retroarcade", 33 * MB, 6),
    appRow("Chattr", "com.hollowpine.chattr", 22 * MB, 14),
    appRow("Zephyr Weather", "com.skygaze.zephyr", 15 * MB, 5),
    appRow("FitPulse", "com.vitalstep.fitpulse", 9 * MB, 7),
    appRow("Trailmark Maps", "com.wanderlab.trailmark", 6 * MB, 4),
    appRow("Pocket Ledger", "com.finlytic.ledger", 3 * MB, 3),
    appRow("Nimbus Notes", "net.brightpixel.nimbus", 1 * MB, 2),
    appRow("Tasklet", "com.brightpixel.tasklet", 512 * KB, 2),
)

@PreviewTest
@PlayStoreLocales
@Composable
fun AppCleanerScreenshot() {
    PreviewWrapper {
        CompositionLocalProvider(LocalPreviewImageProvider provides rememberSampleImageProvider()) {
            AppCleanerListScreen(
                stateSource = MutableStateFlow(
                    AppCleanerListViewModel.State(
                        rows = APP_ROWS,
                        totalCount = APP_ROWS.sumOf { it.junk.inaccessibleCache?.itemCount ?: 0 },
                    ),
                ),
            )
        }
    }
}
