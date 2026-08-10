package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.dialog.SdmAlertDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.dialog.SdmDialogButtonBar
import eu.darken.sdmse.common.compose.focus.dpadVerticalFieldEscape
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

internal const val CRITERIUM_EDITOR_INPUT_TEST_TAG = "customfilter.editor.criterium_input"
internal const val CRITERIUM_EDITOR_MODE_ROW_TEST_TAG = "customfilter.editor.criterium_mode_row"

/** What the dialog's positive button does for the draft as it currently stands. */
private enum class PositiveAction {
    SAVE,
    REMOVE,
    CLOSE,
}

/**
 * Creates or edits exactly one criterium.
 *
 * The candidate criterium is built exactly once via [buildCriterium] and that same instance is
 * used for the duplicate check and handed to [onSave] — validating one construction while saving
 * a differently built one is how a "saved" edit ends up not matching what was checked.
 *
 * [original] is the entry being edited, or null when creating a new one. [siblings] are the other
 * criteria of the same section (i.e. excluding [original]).
 */
@Composable
internal fun CriteriumEditorDialog(
    section: CriteriaSection,
    type: TagType,
    original: SieveCriterium?,
    value: TextFieldValue,
    mode: SieveCriterium.Mode,
    siblings: List<SieveCriterium>,
    onValueChange: (TextFieldValue) -> Unit,
    onChangeMode: () -> Unit,
    onSave: (SieveCriterium) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isNew = original == null
    // Gated on the RAW text, not the trimmed one: a whitespace-only draft is a legitimate
    // criterium (buildCriterium keeps it verbatim), only a truly empty field has nothing to save.
    val candidate = remember(value.text, type, mode) {
        value.text.takeIf { it.isNotEmpty() }?.let { buildCriterium(it, type, mode) }
    }
    val isDuplicate = candidate != null && siblings.contains(candidate)
    val canSave = candidate != null && !isDuplicate
    // Editing an existing entry gives the positive button two extra jobs, because "Save" is a lie
    // in both cases: an emptied field means the user wants the entry gone, and an untouched draft
    // has nothing to write. Creating keeps the plain disabled-until-valid Save.
    val positiveAction = when {
        isNew -> PositiveAction.SAVE
        value.text.isEmpty() -> PositiveAction.REMOVE
        candidate == original -> PositiveAction.CLOSE
        else -> PositiveAction.SAVE
    }
    val duplicateError = stringResource(
        SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_duplicate_error,
    )
    val modeLabel = stringResource(modeLabelRes(mode))
    // Display form, so a whitespace draft names itself as "2 spaces" rather than being spoken as
    // the dangling "for   ,".
    val draft = criteriumDisplayText(normaliseCriteriumInput(value.text))
    // A new entry has no text to name in "Change match mode for X": formatting it with an empty X
    // would have TalkBack announce a dangling "for ,". Without a click label the row falls back to
    // its own visible content (the mode label) plus the standard activation hint, which is complete.
    val modeClickLabel = when {
        draft.isEmpty() -> null
        else -> stringResource(
            SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_mode_x_action,
            draft,
            modeLabel,
        )
    }

    val focusRequester = remember { FocusRequester() }

    SdmAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(criteriumEditorTitleRes(isNew)))
                // The title stays generic; which list is being edited is the subtitle, reusing the
                // section's own label from the editor screen.
                Text(
                    text = stringResource(criteriumSectionLabelRes(section)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            // The focus request MUST live inside this slot: the slot is composed in the dialog
            // window's own subcomposition, so an effect declared in the dialog function's body runs
            // while Modifier.focusRequester below is still unattached. requestFocus() then fails
            // ("FocusRequester is not initialized"), and the field silently opens unfocused — key
            // events go to whatever else the dialog focuses instead of into the text field.
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { onValueChange(if (type == TagType.NAME) stripSlashes(it) else it) },
                    label = {
                        Text(
                            stringResource(
                                SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_value_label,
                            ),
                        )
                    },
                    isError = isDuplicate,
                    supportingText = { if (isDuplicate) Text(duplicateError) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            when (positiveAction) {
                                PositiveAction.SAVE -> if (canSave) onSave(candidate!!)
                                PositiveAction.CLOSE -> onDismiss()
                                // Deleting an entry stays an explicit button press; the IME's Done
                                // key is far too easy to hit by accident for a destructive action.
                                PositiveAction.REMOVE -> Unit
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        // Without this the field swallows D-pad UP/DOWN, and since it now holds
                        // the initial focus a TV remote could never reach the mode row or buttons.
                        .dpadVerticalFieldEscape()
                        // Without this the field swallows D-pad UP/DOWN, and since it now holds
                        // the initial focus a TV remote could never reach the mode row or buttons.
                        .testTag(CRITERIUM_EDITOR_INPUT_TEST_TAG)
                        // M3 only sets a generic "invalid input" error, so screen readers would
                        // never learn WHY the entry is rejected.
                        .semantics { if (isDuplicate) error(duplicateError) },
                )
                Spacer(Modifier.height(8.dp))
                // The row that shows the mode IS the control for changing it — no separate button.
                // clickable() also makes it D-pad focusable.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = modeClickLabel,
                            onClick = onChangeMode,
                        )
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp)
                        .testTag(CRITERIUM_EDITOR_MODE_ROW_TEST_TAG),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = modeIcon(mode),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            SdmDialogButtonBar(
                // The text field owns the focus here; a button claiming it swallows the typing.
                autoFocus = false,
                positive = SdmDialogAction(
                    label = stringResource(
                        when (positiveAction) {
                            PositiveAction.SAVE -> CommonR.string.general_save_action
                            PositiveAction.REMOVE -> CommonR.string.general_remove_action
                            PositiveAction.CLOSE -> CommonR.string.general_close_action
                        },
                    ),
                    enabled = positiveAction != PositiveAction.SAVE || canSave,
                    onClick = {
                        when (positiveAction) {
                            PositiveAction.SAVE -> candidate?.let { onSave(it) }
                            PositiveAction.REMOVE -> onRemove()
                            PositiveAction.CLOSE -> onDismiss()
                        }
                    },
                ),
                negative = SdmDialogAction(
                    label = stringResource(CommonR.string.general_cancel_action),
                    onClick = onDismiss,
                ),
            )
        },
    )
}

@Preview2
@Composable
private fun CriteriumEditorDialogPreview() {
    PreviewWrapper {
        CriteriumEditorDialog(
            section = CriteriaSection.PATH,
            type = TagType.SEGMENTS,
            original = SegmentCriterium(
                segments = listOf("Downloads"),
                mode = SegmentCriterium.Mode.Contain(allowPartial = true),
            ),
            value = TextFieldValue("Downloads/oldapks"),
            mode = SegmentCriterium.Mode.Contain(allowPartial = true),
            siblings = emptyList(),
            onValueChange = {},
            onChangeMode = {},
            onSave = {},
            onRemove = {},
            onDismiss = {},
        )
    }
}
