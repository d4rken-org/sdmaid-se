package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SdmListDefaults {
    val FullWidthContentPadding = PaddingValues(vertical = 8.dp)
    val GridContentPadding = PaddingValues(vertical = 8.dp)
    val GridTileContentPadding = PaddingValues(8.dp)
    val EmptyStatePadding: Dp = 32.dp
    val ToolGridMinWidth: Dp = 410.dp
    val DetailGridMinWidth: Dp = 390.dp

    /**
     * Extra bottom space a scrollable list needs so a floating action button doesn't cover its
     * last item ([androidx.compose.material3.Scaffold] floats the FAB over content without
     * reserving room). FAB height (56dp) + scaffold FAB margin (16dp) + breathing room (16dp).
     */
    val FabClearance: Dp = 88.dp

    /**
     * Extra breathing room below the last item of a scrollable list on scaffolds without a
     * floating action button, so the final entry doesn't sit flush against the screen edge.
     */
    val ContentBottomSpacing: Dp = 16.dp
}

/** Returns a copy of these [PaddingValues] with [extra] added to the bottom. */
@Composable
fun PaddingValues.plusBottom(extra: Dp): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding() + extra,
    )
}
