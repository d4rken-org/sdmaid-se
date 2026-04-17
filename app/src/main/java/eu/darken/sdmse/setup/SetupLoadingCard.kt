package eu.darken.sdmse.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.setup.shizuku.ShizukuIcon
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.ui.R as UiR

data class SetupLoadingCardItem(
    override val state: SetupModule.State.Loading,
) : SetupCardItem

@Composable
internal fun SetupLoadingCard(
    item: SetupLoadingCardItem,
    modifier: Modifier = Modifier,
) {
    val (iconPainter, titleRes) = loadingTypeDetails(item.state.type)
    SetupCardContainer(
        iconPainter = iconPainter,
        title = stringResource(titleRes),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(CommonR.string.general_progress_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun loadingTypeDetails(type: SetupModule.Type): Pair<Painter, Int> = when (type) {
    SetupModule.Type.USAGE_STATS -> painterResource(CommonR.drawable.ic_chartbox_24) to R.string.setup_usagestats_title
    SetupModule.Type.AUTOMATION -> painterResource(UiR.drawable.ic_baseline_accessibility_new_24) to R.string.setup_acs_card_title
    SetupModule.Type.SHIZUKU -> rememberVectorPainter(ShizukuIcon) to R.string.setup_shizuku_card_title
    SetupModule.Type.ROOT -> painterResource(R.drawable.ic_root_24) to R.string.setup_root_card_title
    SetupModule.Type.NOTIFICATION -> painterResource(R.drawable.ic_notification_24) to R.string.setup_notification_title
    SetupModule.Type.SAF -> painterResource(R.drawable.ic_saf) to R.string.setup_saf_card_title
    SetupModule.Type.STORAGE -> painterResource(UiR.drawable.ic_sd_storage) to R.string.setup_manage_storage_card_title
    SetupModule.Type.INVENTORY -> painterResource(CommonR.drawable.ic_apps) to R.string.setup_inventory_card_title
}

@Preview2
@Composable
private fun SetupLoadingCardPreview() {
    PreviewWrapper {
        SetupLoadingCard(
            item = SetupLoadingCardItem(
                state = PreviewLoadingState(SetupModule.Type.SAF),
            ),
        )
    }
}

private data class PreviewLoadingState(
    override val type: SetupModule.Type,
) : SetupModule.State.Loading {
    override val startAt: java.time.Instant = java.time.Instant.now()
}
