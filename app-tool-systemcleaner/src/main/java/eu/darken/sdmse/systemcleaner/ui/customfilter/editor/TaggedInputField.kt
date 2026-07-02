package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.sieve.SieveCriterium

internal const val TAGGED_INPUT_FIELD_TEST_TAG = "customfilter.editor.tagged_input"

@Composable
internal fun TaggedInputField(
    modifier: Modifier = Modifier,
    type: TagType,
    hint: String,
    tags: List<SieveCriterium>,
    onAdd: (SieveCriterium) -> Unit,
    onRemove: (SieveCriterium) -> Unit,
    onModeChange: (old: SieveCriterium, new: SieveCriterium) -> Unit,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    // Saveable: a config change mid-edit of a backspace-popped chip must not lose the chip — it
    // was already removed from the draft, so the in-progress text is its only remaining copy.
    var typedText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    // The chip that was popped back into the input for editing (null while typing a fresh entry).
    var editingCriterium by rememberSaveable { mutableStateOf<SieveCriterium?>(null) }
    var modeSwitcherFor by rememberSaveable { mutableStateOf<SieveCriterium?>(null) }

    fun commit() {
        val text = typedText.text.trim()
        if (text.isNotEmpty()) onAdd(inputTextToChipTag(text, type, editingCriterium))
        editingCriterium = null
        typedText = TextFieldValue("")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = hint,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tags.forEach { criterium ->
                TaggedChip(
                    criterium = criterium,
                    onRemove = { onRemove(criterium) },
                    onLongClick = { modeSwitcherFor = criterium },
                )
            }
            BasicTextField(
                value = typedText,
                onValueChange = { newValue ->
                    typedText = if (type == TagType.NAME) stripSlashes(newValue) else newValue
                },
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .testTag(TAGGED_INPUT_FIELD_TEST_TAG)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            // Don't silently drop an in-progress edit of a popped chip — re-commit it
                            // (preserving its matching mode). Fresh, un-submitted drafts are discarded.
                            if (editingCriterium != null) {
                                commit()
                            } else if (typedText.text.isNotEmpty()) {
                                typedText = TextFieldValue("")
                            }
                        }
                        onFocusChange?.invoke(focusState.isFocused)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace &&
                            typedText.text.isEmpty() &&
                            tags.isNotEmpty()
                        ) {
                            val last = tags.last()
                            onRemove(last)
                            editingCriterium = last
                            val value = criteriumValue(last)
                            // Caret at the end so cursor keys move within the text instead of escaping focus.
                            typedText = TextFieldValue(value, selection = TextRange(value.length))
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { commit() },
                    onSearch = { commit() },
                ),
            )
        }
    }

    modeSwitcherFor?.let { active ->
        ChipModeSwitcherDialog(
            criterium = active,
            onModeSelected = { newMode ->
                onModeChange(active, withMode(active, newMode))
                modeSwitcherFor = null
            },
            onDismiss = { modeSwitcherFor = null },
        )
    }
}
