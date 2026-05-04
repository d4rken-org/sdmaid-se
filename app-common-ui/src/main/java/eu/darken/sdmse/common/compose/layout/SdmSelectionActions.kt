package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.ShieldAdd
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
fun SdmDeleteAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Delete,
            contentDescription = stringResource(CommonR.string.general_delete_selected_action),
        )
    }
}

@Composable
fun SdmExcludeAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
    ) {
        Icon(
            imageVector = SdmIcons.ShieldAdd,
            contentDescription = stringResource(CommonR.string.general_exclude_selected_action),
        )
    }
}

@Composable
fun SdmSelectAllAction(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    IconButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.TwoTone.SelectAll,
            contentDescription = stringResource(CommonR.string.general_list_select_all_action),
        )
    }
}

@Preview2
@Composable
private fun SdmSelectionActionsPreview() {
    PreviewWrapper {
        Row {
            SdmSelectAllAction(
                visible = true,
                onClick = {},
            )
            SdmExcludeAction(onClick = {})
            SdmDeleteAction(onClick = {})
        }
    }
}
