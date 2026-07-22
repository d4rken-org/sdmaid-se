package eu.darken.sdmse.corpsefinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.ui.list.CorpseFinderListScreen
import eu.darken.sdmse.corpsefinder.ui.list.CorpseFinderListViewModel
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpseRow
import eu.darken.sdmse.corpsefinder.ui.preview.previewLocalPathLookup
import kotlinx.coroutines.flow.MutableStateFlow

// Play Store screenshot entry point for the CorpseFinder list. Rows use distinct paths (unique
// identifiers) and vary RiskLevel across NORMAL / KEEPER / COMMON.

private fun corpseRow(dirName: String, riskLevel: RiskLevel) = previewCorpseRow(
    previewCorpse(
        lookup = previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", dirName),
        ),
        riskLevel = riskLevel,
    ),
)

@PreviewTest
@PlayStoreLocales
@Composable
fun CorpseFinderScreenshot() {
    PreviewWrapper {
        CorpseFinderListScreen(
            stateSource = MutableStateFlow(
                CorpseFinderListViewModel.State(
                    rows = listOf(
                        corpseRow("com.mojang.minecraftpe", RiskLevel.NORMAL),
                        corpseRow("com.rovio.angrybirds", RiskLevel.COMMON),
                        corpseRow("com.dropbox.android", RiskLevel.NORMAL),
                        corpseRow("com.adobe.reader", RiskLevel.KEEPER),
                        corpseRow("com.soundcloud.android", RiskLevel.NORMAL),
                    ),
                ),
            ),
        )
    }
}
