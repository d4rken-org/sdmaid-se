package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.sieve.SieveCriterium

@Composable
internal fun ChipModeSwitcherDialog(
    criterium: SieveCriterium,
    onModeSelected: (SieveCriterium.Mode) -> Unit,
    onDismiss: () -> Unit,
) {
    val modes = remember(criterium) { availableModesFor(criterium) }
    val selectedIndex = remember(criterium) {
        modes.indexOfFirst { it.first::class.isInstance(criterium.mode) }.coerceAtLeast(0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(modeSwitcherTitleRes(criterium))) },
        text = {
            Column {
                modes.forEachIndexed { index, (mode, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onModeSelected(mode) },
                        )
                        Text(stringResource(labelRes))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}
