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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.sieve.SieveCriterium

@Composable
internal fun TaggedInputField(
    type: TagType,
    hint: String,
    tags: List<SieveCriterium>,
    onAdd: (SieveCriterium) -> Unit,
    onRemove: (SieveCriterium) -> Unit,
    onModeChange: (old: SieveCriterium, new: SieveCriterium) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    var typedText by remember { mutableStateOf("") }
    var modeSwitcherFor by remember { mutableStateOf<SieveCriterium?>(null) }

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
                    typedText = if (type == TagType.NAME) newValue.filterNot { it == '/' } else newValue
                },
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && typedText.isNotEmpty()) typedText = ""
                        onFocusChange?.invoke(focusState.isFocused)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace &&
                            typedText.isEmpty() &&
                            tags.isNotEmpty()
                        ) {
                            val last = tags.last()
                            onRemove(last)
                            typedText = criteriumValue(last)
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
                    onDone = {
                        val text = typedText.trim()
                        if (text.isNotEmpty()) {
                            onAdd(inputTextToChipTag(text, type))
                            typedText = ""
                        }
                    },
                    onSearch = {
                        val text = typedText.trim()
                        if (text.isNotEmpty()) {
                            onAdd(inputTextToChipTag(text, type))
                            typedText = ""
                        }
                    },
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
