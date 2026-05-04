package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SdmListDefaults {
    val FullWidthContentPadding = PaddingValues(vertical = 8.dp)
    val CardContentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val GridContentPadding = PaddingValues(vertical = 8.dp)
    val GridTileContentPadding = PaddingValues(8.dp)
    val EmptyStatePadding: Dp = 32.dp
    val ToolGridMinWidth: Dp = 410.dp
    val DetailGridMinWidth: Dp = 390.dp
}
