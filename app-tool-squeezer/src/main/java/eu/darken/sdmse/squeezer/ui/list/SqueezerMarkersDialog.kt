package eu.darken.sdmse.squeezer.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.ui.list.items.CompressedBeforeChip
import eu.darken.sdmse.squeezer.ui.list.items.HdrDepthChip

@Composable
internal fun SqueezerMarkersDialog(onDismiss: () -> Unit) {
    SdmConfirmDialog(
        title = stringResource(R.string.squeezer_markers_dialog_title),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(CommonR.string.general_close_action),
            onClick = onDismiss,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MarkerExplanation(
                marker = { CompressedBeforeChip() },
                title = stringResource(R.string.squeezer_chip_compressed_before),
                description = stringResource(R.string.squeezer_markers_compressed_before_description),
            )
            MarkerExplanation(
                marker = { HdrDepthChip() },
                title = stringResource(R.string.squeezer_chip_hdr_depth),
                description = stringResource(R.string.squeezer_markers_hdr_depth_description),
            )
        }
    }
}

/**
 * The marker slot renders the same chip composable the list rows use, so the legend can't drift
 * away from what it explains.
 */
@Composable
private fun MarkerExplanation(
    marker: @Composable () -> Unit,
    title: String,
    description: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .width(148.dp)
                .padding(end = 12.dp, top = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) { marker() }
        Column(modifier = Modifier.padding(top = 1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview2
@Composable
private fun SqueezerMarkersDialogPreview() {
    PreviewWrapper {
        SqueezerMarkersDialog(onDismiss = {})
    }
}
