package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.ShieldAdd
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Exclude/delete pair that acts on a whole scope (a filter, an app, a corpse, a cluster) rather than
 * on the current selection. [enabled] has no default so a new caller has to decide what happens while
 * a selection is active.
 */
@Composable
fun SdmWholeScopeActions(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onExclude: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onExclude,
            modifier = Modifier.weight(1f),
            enabled = enabled,
        ) {
            Icon(SdmIcons.ShieldAdd, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(CommonR.string.general_exclude_action))
        }
        Button(
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.TwoTone.DeleteSweep, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(CommonR.string.general_delete_action))
        }
    }
}

@Preview2
@Composable
private fun SdmWholeScopeActionsPreview() {
    PreviewWrapper {
        SdmWholeScopeActions(
            enabled = true,
            onExclude = {},
            onDelete = {},
        )
    }
}
