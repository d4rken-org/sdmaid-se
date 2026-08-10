package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import eu.darken.sdmse.common.R as CommonR

internal enum class EditorStep {
    EDITOR,
    MODE,
}

/**
 * A single create-or-edit interaction with one criterium.
 *
 * [original] is the identity of the entry being edited and is NEVER replaced by the draft: `null`
 * means create (Save adds), otherwise Save swaps `original -> candidate`. Targeting the draft
 * instead would make Save a silent no-op, because [swapPreservingOrder] returns the set unchanged
 * when `old` isn't in it. Visiting the mode dialog mutates [draftMode] and [step] only.
 */
internal data class EditorSession(
    val original: SieveCriterium?,
    val draftText: TextFieldValue,
    val draftMode: SieveCriterium.Mode,
    val step: EditorStep = EditorStep.EDITOR,
)

private const val SESSION_KEY_ORIGINAL = "original"
private const val SESSION_KEY_TEXT = "text"
private const val SESSION_KEY_SELECTION_START = "selectionStart"
private const val SESSION_KEY_SELECTION_END = "selectionEnd"
private const val SESSION_KEY_MODE = "mode"
private const val SESSION_KEY_STEP = "step"

/**
 * [rememberSaveable] can't save an arbitrary data class, and [TextFieldValue] isn't Parcelable,
 * so the session is stored field-wise (criterium and mode are Parcelable, the rest primitives).
 */
internal val EditorSessionSaver: Saver<EditorSession?, Any> = mapSaver(
    save = { session ->
        if (session == null) {
            emptyMap()
        } else {
            mapOf(
                SESSION_KEY_ORIGINAL to session.original,
                SESSION_KEY_TEXT to session.draftText.text,
                SESSION_KEY_SELECTION_START to session.draftText.selection.start,
                SESSION_KEY_SELECTION_END to session.draftText.selection.end,
                SESSION_KEY_MODE to session.draftMode,
                SESSION_KEY_STEP to session.step.name,
            )
        }
    },
    restore = { saved ->
        if (saved.isEmpty()) {
            null
        } else {
            EditorSession(
                original = saved[SESSION_KEY_ORIGINAL] as SieveCriterium?,
                draftText = TextFieldValue(
                    text = saved[SESSION_KEY_TEXT] as String,
                    selection = TextRange(
                        saved[SESSION_KEY_SELECTION_START] as Int,
                        saved[SESSION_KEY_SELECTION_END] as Int,
                    ),
                ),
                draftMode = saved[SESSION_KEY_MODE] as SieveCriterium.Mode,
                step = EditorStep.valueOf(saved[SESSION_KEY_STEP] as String),
            )
        }
    },
)

/**
 * Renders one criteria section: the existing entries as chips plus an add button. Every mutation
 * goes through a dialog — there is no inline text field, because its limbo state (a chip popped
 * into the input) could destroy entries without any visible trace.
 */
@Composable
internal fun TaggedInputField(
    modifier: Modifier = Modifier,
    type: TagType,
    section: CriteriaSection,
    hint: String,
    tags: List<SieveCriterium>,
    onAdd: (SieveCriterium) -> Unit,
    onRemove: (SieveCriterium) -> Unit,
    onSwap: (old: SieveCriterium, new: SieveCriterium) -> Unit,
) {
    var session by rememberSaveable(stateSaver = EditorSessionSaver) {
        mutableStateOf<EditorSession?>(null)
    }
    var modeSwitcherFor by rememberSaveable { mutableStateOf<SieveCriterium?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = hint,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Surface applies minimumInteractiveComponentSize(), so a 32dp chip occupies 48dp of layout
        // and the rows drift far apart. 40dp keeps the remove target comfortably tappable.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                tags.forEach { criterium ->
                    TaggedChip(
                        criterium = criterium,
                        onEdit = {
                            val text = criteriumValue(criterium)
                            session = EditorSession(
                                original = criterium,
                                draftText = TextFieldValue(text, TextRange(text.length)),
                                draftMode = criteriumMode(criterium),
                            )
                        },
                        onRemove = { onRemove(criterium) },
                        onShowModeSwitcher = { modeSwitcherFor = criterium },
                    )
                }
                AddCriteriumChip(
                    section = section,
                    onClick = {
                        session = EditorSession(
                            original = null,
                            draftText = TextFieldValue(""),
                            draftMode = defaultModeFor(type),
                        )
                    },
                )
            }
        }
    }

    session?.let { active ->
        val original = active.original
        val siblings = tags.filter { it != original }
        when (active.step) {
            EditorStep.EDITOR -> CriteriumEditorDialog(
                section = section,
                type = type,
                original = original,
                value = active.draftText,
                mode = active.draftMode,
                siblings = siblings,
                onValueChange = { session = active.copy(draftText = it) },
                onChangeMode = { session = active.copy(step = EditorStep.MODE) },
                onSave = { candidate ->
                    if (original == null) onAdd(candidate) else onSwap(original, candidate)
                    session = null
                },
                // Only offered while editing, so original is non-null; routed through the same
                // remove path as the chip's own remove button.
                onRemove = {
                    original?.let { onRemove(it) }
                    session = null
                },
                onDismiss = { session = null },
            )

            EditorStep.MODE -> ChipModeSwitcherDialog(
                type = type,
                selectedMode = active.draftMode,
                unavailableModes = collidingModes(active.draftText.text, type, siblings),
                // Only the mode changes: the draft text and the edited entry's identity survive.
                onModeSelected = { session = active.copy(draftMode = it, step = EditorStep.EDITOR) },
                onDismiss = { session = active.copy(step = EditorStep.EDITOR) },
            )
        }
    }

    modeSwitcherFor?.let { target ->
        val siblings = tags.filter { it != target }
        // The candidate that gets validated MUST be the one that gets saved. Rebuilding it from the
        // chip's text (which trims/normalises) while saving a withMode() copy (which doesn't) lets a
        // collision slip past the check for entries carrying e.g. surrounding whitespace — the save
        // then writes an exact duplicate and swapPreservingOrder collapses it, losing a chip.
        val candidatesByMode = availableModesFor(type)
            .map { it.first }
            .associateWith { withMode(target, it) }
        ChipModeSwitcherDialog(
            type = type,
            selectedMode = criteriumMode(target),
            unavailableModes = candidatesByMode.filterValues { siblings.contains(it) }.keys,
            onModeSelected = { newMode ->
                onSwap(target, candidatesByMode.getValue(newMode))
                modeSwitcherFor = null
            },
            onDismiss = { modeSwitcherFor = null },
        )
    }
}

@Composable
private fun AddCriteriumChip(
    section: CriteriaSection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(criteriumAddDescriptionRes(section))
    AssistChip(
        onClick = onClick,
        // The visible label stays short so the chip row keeps its density; screen readers get the
        // section-specific wording instead of a bare "Add".
        modifier = modifier.semantics { contentDescription = description },
        label = {
            Text(stringResource(CommonR.string.general_add_action))
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.TwoTone.Add,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

@Preview2
@Composable
private fun TaggedInputFieldPreview() {
    PreviewWrapper {
        TaggedInputField(
            type = TagType.SEGMENTS,
            section = CriteriaSection.PATH,
            hint = "Path criteria",
            tags = listOf(
                SegmentCriterium(listOf("Downloads"), SegmentCriterium.Mode.Contain(allowPartial = true)),
                SegmentCriterium(listOf("tmp"), SegmentCriterium.Mode.Equal()),
            ),
            onAdd = {},
            onRemove = {},
            onSwap = { _, _ -> },
        )
    }
}
