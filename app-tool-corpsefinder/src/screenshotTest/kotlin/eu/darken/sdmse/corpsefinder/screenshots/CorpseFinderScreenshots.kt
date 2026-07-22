package eu.darken.sdmse.corpsefinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.core.filter.AppLibCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.AppSourceCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.DalvikCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PrivateDataCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PublicDataCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PublicMediaCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PublicObbCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.SdcardCorpseFilter
import eu.darken.sdmse.corpsefinder.ui.list.CorpseFinderListScreen
import eu.darken.sdmse.corpsefinder.ui.list.CorpseFinderListViewModel
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpseRow
import eu.darken.sdmse.corpsefinder.ui.preview.previewLocalPathLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KClass

// Play Store screenshot entry point for the CorpseFinder list. Fictional paths only (no real
// packages). Rows span several corpse filter types (different source labels), risk levels, and
// varied child counts/sizes so the screen shows a realistic, full scan.

private const val KB = 1024L
private const val MB = 1024L * KB

private fun corpse(
    filterType: KClass<out CorpseFilter>,
    pathSegments: Array<String>,
    childSizes: List<Long>,
    riskLevel: RiskLevel = RiskLevel.NORMAL,
) = previewCorpseRow(
    previewCorpse(
        filterType = filterType,
        lookup = previewLocalPathLookup(pathSegments = pathSegments, size = childSizes.sum()),
        content = childSizes.mapIndexed { i, s ->
            previewLocalPathLookup(pathSegments = pathSegments + "item_$i", size = s)
        },
        riskLevel = riskLevel,
    ),
)

private val CORPSE_ROWS = listOf(
    corpse(PublicObbCorpseFilter::class, arrayOf("storage", "emulated", "0", "Android", "obb", "com.pixelforge.retroarcade"), listOf(92 * MB), RiskLevel.KEEPER),
    corpse(AppSourceCorpseFilter::class, arrayOf("data", "app", "com.paperleaf.lumen-1"), listOf(24 * MB)),
    corpse(PublicMediaCorpseFilter::class, arrayOf("storage", "emulated", "0", "Android", "media", "com.audionest.beatstream"), listOf(12 * MB, 6 * MB)),
    corpse(PrivateDataCorpseFilter::class, arrayOf("data", "data", "com.hollowpine.chattr"), listOf(8 * MB, 512 * KB)),
    corpse(SdcardCorpseFilter::class, arrayOf("storage", "emulated", "0", "Trailmark_Export"), listOf(4 * MB, 1200 * KB)),
    corpse(DalvikCorpseFilter::class, arrayOf("data", "dalvik-cache", "arm64", "zephyr@classes.dex"), listOf(3 * MB), RiskLevel.COMMON),
    corpse(AppLibCorpseFilter::class, arrayOf("data", "app-lib", "com.vitalstep.fitpulse"), listOf(1200 * KB)),
    corpse(SdcardCorpseFilter::class, arrayOf("storage", "emulated", "0", ".thumbcache_backup"), listOf(640 * KB)),
    corpse(PublicDataCorpseFilter::class, arrayOf("storage", "emulated", "0", "Android", "data", "com.wanderlab.voyage"), listOf(140 * KB, 60 * KB, 8 * KB)),
    corpse(PublicDataCorpseFilter::class, arrayOf("storage", "emulated", "0", "Android", "data", "com.brightpixel.snapvault"), listOf(63 * KB, 12 * KB), RiskLevel.COMMON),
    corpse(PublicMediaCorpseFilter::class, arrayOf("storage", "emulated", "0", "Android", "media", "com.skygaze.zephyr"), listOf(24 * KB)),
)

@PreviewTest
@PlayStoreLocales
@Composable
fun CorpseFinderScreenshot() {
    PreviewWrapper {
        CorpseFinderListScreen(
            stateSource = MutableStateFlow(
                CorpseFinderListViewModel.State(rows = CORPSE_ROWS),
            ),
        )
    }
}
