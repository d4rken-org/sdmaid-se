package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.dialog.SdmAlertDialog
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

/**
 * Picks the matching mode for a criterium.
 *
 * [unavailableModes] renders those options disabled: a mode that another entry already uses for
 * the same text would produce an exact duplicate, which the criteria set collapses — the entry
 * would silently disappear with no dialog left to warn in.
 */
@Composable
internal fun ChipModeSwitcherDialog(
    type: TagType,
    selectedMode: SieveCriterium.Mode,
    onModeSelected: (SieveCriterium.Mode) -> Unit,
    onDismiss: () -> Unit,
    unavailableModes: Set<SieveCriterium.Mode> = emptySet(),
) {
    val modes = remember(type) { availableModesFor(type) }
    val selectedIndex = remember(type, selectedMode) {
        modes.indexOfFirst { it.first::class.isInstance(selectedMode) }.coerceAtLeast(0)
    }

    SdmAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(modeSwitcherTitleRes(type))) },
        text = {
            Column {
                modes.forEachIndexed { index, (mode, labelRes) ->
                    val enabled = !unavailableModes.contains(mode)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selectedIndex,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onModeSelected(mode) },
                            )
                            // RadioButton has onClick = null, so no 48dp minimum applies implicitly.
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            enabled = enabled,
                            onClick = null,
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = stringResource(labelRes),
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (!enabled) {
                                Text(
                                    text = stringResource(
                                        SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_mode_taken_label,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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

@Preview2
@Composable
private fun ChipModeSwitcherDialogPreview() {
    PreviewWrapper {
        ChipModeSwitcherDialog(
            type = TagType.SEGMENTS,
            selectedMode = SegmentCriterium.Mode.Contain(allowPartial = true),
            unavailableModes = setOf(SegmentCriterium.Mode.Equal()),
            onModeSelected = {},
            onDismiss = {},
        )
    }
}
