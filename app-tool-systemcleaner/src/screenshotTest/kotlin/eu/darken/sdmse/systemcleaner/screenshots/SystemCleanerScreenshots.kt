package eu.darken.sdmse.systemcleaner.screenshots

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.FilterAlt
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.systemcleaner.ui.list.SystemCleanerListScreen
import eu.darken.sdmse.systemcleaner.ui.list.SystemCleanerListViewModel
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterItems
import eu.darken.sdmse.systemcleaner.ui.preview.previewSystemCleanerRow
import kotlinx.coroutines.flow.MutableStateFlow

// Play Store screenshot entry point for the SystemCleaner list. A mix of stock filters and user
// "custom" filters (FilterAlt icon + generic description), with varied item counts and sizes.

private const val KB = 1024L
private const val MB = 1024L * KB

private fun filterRow(
    id: String,
    icon: ImageVector,
    label: String,
    description: String,
    items: Int,
    size: Long,
) = previewSystemCleanerRow(
    previewFilterContent(
        identifier = id,
        icon = icon,
        label = label,
        description = description,
        items = previewFilterItems(itemCount = items, totalSize = size),
    ),
)

private val FILTER_ROWS = listOf(
    filterRow("stock.superfluous_apks", Icons.TwoTone.Adb, "Superfluous APKs", "Setup files that are older or equal to installed app versions.", 12, 849 * MB),
    filterRow("custom.evidence-a91f", Icons.TwoTone.FilterAlt, "Wipe out the evidence", "Custom filter", 717, 123 * MB),
    filterRow("stock.temp_files", Icons.TwoTone.Delete, "Temporary files", "Leftover temporary files from all over the device.", 63, 96 * MB),
    filterRow("stock.offline_tiles", Icons.TwoTone.Map, "Offline map tiles", "Cached map areas that can be downloaded again.", 8, 210 * MB),
    filterRow("stock.thumbnails", Icons.TwoTone.Image, "Thumbnail images", "Cached previews for images and videos.", 18, 19 * MB),
    filterRow("stock.ad_caches", Icons.TwoTone.Memory, "Advertisement caches", "Cached advertisements from multiple apps.", 40, 12 * MB),
    filterRow("stock.log_files", Icons.TwoTone.Description, "Log files", "Diagnostic logs left behind by apps.", 24, 4 * MB),
    filterRow("stock.screenshots", Icons.TwoTone.PhoneAndroid, "Screenshots", "Screenshots older than 30 days from multiple apps.", 10, 2900 * KB),
    filterRow("custom.no_dupes-7c22", Icons.TwoTone.FilterAlt, "No duplicates (ignore stock imgs)", "Custom filter", 3, 88 * KB),
    filterRow("stock.empty_dirs", Icons.TwoTone.Folder, "Empty folders", "Empty folders from all over the device.", 11, 37 * KB),
)

@PreviewTest
@PlayStoreLocales
@Composable
fun SystemCleanerScreenshot() {
    PreviewWrapper {
        SystemCleanerListScreen(
            stateSource = MutableStateFlow(
                SystemCleanerListViewModel.State(rows = FILTER_ROWS),
            ),
        )
    }
}
