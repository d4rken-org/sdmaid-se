package eu.darken.sdmse.scheduler.ui.manager.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.scheduler.R

@Composable
internal fun CommandsEditDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    SdmConfirmDialog(
        title = stringResource(R.string.scheduler_commands_after_schedule_label),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(CommonR.string.general_save_action),
            onClick = { onConfirm(text) },
        ),
        negative = SdmDialogAction(
            label = stringResource(CommonR.string.general_cancel_action),
            onClick = onDismiss,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.scheduler_commands_after_schedule_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 4,
                maxLines = 8,
                placeholder = { Text("reboot") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
