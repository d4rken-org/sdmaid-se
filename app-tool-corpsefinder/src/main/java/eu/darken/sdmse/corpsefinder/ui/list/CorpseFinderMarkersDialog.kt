package eu.darken.sdmse.corpsefinder.ui.list

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.corpsefinder.R

@Composable
internal fun CorpseFinderMarkersDialog(onDismiss: () -> Unit) {
    SdmConfirmDialog(
        title = stringResource(R.string.corpsefinder_markers_dialog_title),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(CommonR.string.general_close_action),
            onClick = onDismiss,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MarkerExplanation(
                marker = {
                    MarkerSwatch(color = MaterialTheme.colorScheme.outlineVariant)
                },
                title = stringResource(R.string.corpsefinder_markers_dialog_normal_label),
                description = stringResource(R.string.corpsefinder_markers_dialog_normal_description),
            )
            MarkerExplanation(
                marker = {
                    MarkerPill(
                        label = stringResource(R.string.corpsefinder_risk_keeper_chip),
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                },
                title = stringResource(R.string.corpsefinder_settings_risk_keeper_title),
                description = stringResource(R.string.corpsefinder_corpse_hint_keeper),
            )
            MarkerExplanation(
                marker = {
                    MarkerPill(
                        label = stringResource(R.string.corpsefinder_risk_common_chip),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                },
                title = stringResource(R.string.corpsefinder_settings_risk_common_title),
                description = stringResource(R.string.corpsefinder_corpse_hint_common),
            )
        }
    }
}

@Composable
private fun MarkerExplanation(
    marker: @Composable () -> Unit,
    title: String,
    description: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(width = 84.dp, height = 22.dp)
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

@Composable
private fun MarkerSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 18.dp)
            .border(width = 1.dp, color = color, shape = RoundedCornerShape(6.dp)),
    )
}

@Composable
private fun MarkerPill(label: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.18f),
        contentColor = accent,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Preview2
@Composable
private fun CorpseFinderMarkersDialogPreview() {
    PreviewWrapper {
        CorpseFinderMarkersDialog(onDismiss = {})
    }
}
