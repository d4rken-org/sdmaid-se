package eu.darken.sdmse.systemcleaner.screenshots

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.systemcleaner.ui.list.SystemCleanerListScreen
import eu.darken.sdmse.systemcleaner.ui.list.SystemCleanerListViewModel
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
import eu.darken.sdmse.systemcleaner.ui.preview.previewSystemCleanerRow
import kotlinx.coroutines.flow.MutableStateFlow

// Play Store screenshot entry point for the SystemCleaner list. Rows use distinct filter
// identifiers so LazyColumn keys stay unique.

private fun filterRow(id: String, icon: ImageVector, label: String, description: String) =
    previewSystemCleanerRow(
        previewFilterContent(identifier = id, icon = icon, label = label, description = description),
    )

@PreviewTest
@PlayStoreLocales
@Composable
fun SystemCleanerScreenshot() {
    PreviewWrapper {
        SystemCleanerListScreen(
            stateSource = MutableStateFlow(
                SystemCleanerListViewModel.State(
                    rows = listOf(
                        filterRow("preview.empty_dirs", Icons.TwoTone.Folder, "Empty directories", "Folders that contain no files."),
                        filterRow("preview.temp_files", Icons.TwoTone.Delete, "Temporary files", "Leftover temporary and junk files."),
                        filterRow("preview.thumbnails", Icons.TwoTone.Image, "Thumbnails", "Regeneratable image thumbnails."),
                        filterRow("preview.log_files", Icons.TwoTone.Description, "Log files", "Old application log files."),
                        filterRow("preview.analytics", Icons.TwoTone.Memory, "Advertisement & Analytics", "Data left by ad and analytics libraries."),
                    ),
                ),
            ),
        )
    }
}
