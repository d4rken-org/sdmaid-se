package eu.darken.sdmse.common.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
fun SystemAppChip(modifier: Modifier = Modifier) = SdmInfoChip(
    modifier = modifier,
    icon = painterResource(CommonR.drawable.ic_apps),
    label = stringResource(CommonR.string.general_tag_system),
    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
)

@Preview2
@Composable
private fun SystemAppChipPreview() {
    PreviewWrapper {
        SystemAppChip()
    }
}
