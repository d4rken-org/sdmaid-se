package eu.darken.sdmse.squeezer.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import eu.darken.sdmse.squeezer.ui.list.items.DownscaledChip
import eu.darken.sdmse.squeezer.ui.list.items.HdrDepthChip
import eu.darken.sdmse.squeezer.ui.list.items.MotionPhotoChip

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
                description = stringResource(R.string.squeezer_markers_compressed_before_description),
            )
            MarkerExplanation(
                marker = { HdrDepthChip() },
                description = stringResource(R.string.squeezer_markers_hdr_depth_description),
            )
            MarkerExplanation(
                marker = { MotionPhotoChip() },
                description = stringResource(R.string.squeezer_markers_motion_photo_description),
            )
            MarkerExplanation(
                marker = { DownscaledChip() },
                description = stringResource(R.string.squeezer_markers_downscaled_description),
            )
        }
    }
}

/**
 * The chip is the heading: it renders the same composable the list rows use, so the legend can't
 * drift away from what it explains.
 */
@Composable
private fun MarkerExplanation(
    marker: @Composable () -> Unit,
    description: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        marker()
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview2
@Composable
private fun SqueezerMarkersDialogPreview() {
    PreviewWrapper {
        SqueezerMarkersDialog(onDismiss = {})
    }
}
