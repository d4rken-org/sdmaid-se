package eu.darken.sdmse.exclusion.ui.editor.segment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.exclusion.R
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import eu.darken.sdmse.exclusion.ui.SegmentExclusionEditorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SegmentExclusionEditorScreenHost(
    route: SegmentExclusionEditorRoute,
    vm: SegmentExclusionViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(Unit) { vm.setArgs(route.exclusionId, route.initial) }

    var pendingRemove by remember { mutableStateOf(false) }
    var pendingCancel by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                SegmentExclusionViewModel.Event.RemoveConfirmation -> pendingRemove = true
                SegmentExclusionViewModel.Event.UnsavedChangesConfirmation -> pendingCancel = true
            }
        }
    }

    BackHandler { vm.cancel() }

    SegmentExclusionEditorScreen(
        stateSource = vm.state,
        onNavigateBack = { vm.cancel() },
        onUpdateSegments = vm::updateSegments,
        onToggleAllowPartial = vm::toggleAllowPartial,
        onToggleIgnoreCase = vm::toggleIgnoreCase,
        onToggleTag = vm::toggleTag,
        onSave = vm::save,
        onRemove = { vm.remove() },
    )

    if (pendingRemove) {
        AlertDialog(
            onDismissRequest = { pendingRemove = false },
            text = { Text(stringResource(R.string.exclusion_editor_remove_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemove = false
                    vm.remove(confirmed = true)
                }) { Text(stringResource(CommonR.string.general_remove_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }

    if (pendingCancel) {
        AlertDialog(
            onDismissRequest = { pendingCancel = false },
            text = { Text(stringResource(CommonR.string.general_unsaved_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingCancel = false
                    vm.cancel(confirmed = true)
                }) { Text(stringResource(CommonR.string.general_discard_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

@Composable
internal fun SegmentExclusionEditorScreen(
    stateSource: StateFlow<SegmentExclusionViewModel.State> = MutableStateFlow(SegmentExclusionViewModel.State.Loading),
    onNavigateBack: () -> Unit = {},
    onUpdateSegments: (String) -> Unit = {},
    onToggleAllowPartial: () -> Unit = {},
    onToggleIgnoreCase: () -> Unit = {},
    onToggleTag: (Exclusion.Tag) -> Unit = {},
    onSave: () -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exclusion_type_segment)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val ready = state as? SegmentExclusionViewModel.State.Ready
                    if (ready?.canRemove == true) {
                        IconButton(onClick = onRemove) {
                            Icon(
                                imageVector = Icons.TwoTone.Delete,
                                contentDescription = stringResource(CommonR.string.general_remove_action),
                            )
                        }
                    }
                    if (ready?.canSave == true) {
                        IconButton(onClick = onSave) {
                            Icon(
                                imageVector = Icons.TwoTone.Save,
                                contentDescription = stringResource(CommonR.string.general_save_action),
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val s = state) {
            SegmentExclusionViewModel.State.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            SegmentExclusionViewModel.State.NotFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(CommonR.string.general_empty_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is SegmentExclusionViewModel.State.Ready -> ReadyContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                ready = s,
                onUpdateSegments = onUpdateSegments,
                onToggleAllowPartial = onToggleAllowPartial,
                onToggleIgnoreCase = onToggleIgnoreCase,
                onToggleTag = onToggleTag,
            )
        }
    }
}

@Composable
private fun ReadyContent(
    modifier: Modifier = Modifier,
    ready: SegmentExclusionViewModel.State.Ready,
    onUpdateSegments: (String) -> Unit,
    onToggleAllowPartial: () -> Unit,
    onToggleIgnoreCase: () -> Unit,
    onToggleTag: (Exclusion.Tag) -> Unit,
) {
    var inputText by remember { mutableStateOf(ready.current.segments.joinSegments()) }
    val allowPartial = ready.current.allowPartial
    val ignoreCase = ready.current.ignoreCase

    val demoText = run {
        var demo = ready.current.segments.joinSegments()
        if (allowPartial) demo = "*$demo*"
        if (ignoreCase) demo = demo.lowercase()
        demo
    }
    val hasContent = ready.current.segments.any { it.isNotEmpty() }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.exclusion_target_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        onUpdateSegments(it)
                    },
                    placeholder = { Text(stringResource(R.string.exclusion_edit_action)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
                if (hasContent) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = demoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(CommonR.string.general_options_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                SwitchRow(
                    label = stringResource(R.string.exclusion_option_allow_partial),
                    checked = allowPartial,
                    onToggle = onToggleAllowPartial,
                )
                SwitchRow(
                    label = stringResource(R.string.exclusion_option_ignore_casing),
                    checked = ignoreCase,
                    onToggle = onToggleIgnoreCase,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.exclusion_editor_affected_tools_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(8.dp))
                TagToggle(
                    label = stringResource(R.string.exclusion_tags_alltools),
                    checked = ready.current.tags.contains(Exclusion.Tag.GENERAL),
                    onToggle = { onToggleTag(Exclusion.Tag.GENERAL) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.corpsefinder_tool_name),
                    checked = ready.current.tags.contains(Exclusion.Tag.CORPSEFINDER),
                    onToggle = { onToggleTag(Exclusion.Tag.CORPSEFINDER) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.systemcleaner_tool_name),
                    checked = ready.current.tags.contains(Exclusion.Tag.SYSTEMCLEANER),
                    onToggle = { onToggleTag(Exclusion.Tag.SYSTEMCLEANER) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.appcleaner_tool_name),
                    checked = ready.current.tags.contains(Exclusion.Tag.APPCLEANER),
                    onToggle = { onToggleTag(Exclusion.Tag.APPCLEANER) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.deduplicator_tool_name),
                    checked = ready.current.tags.contains(Exclusion.Tag.DEDUPLICATOR),
                    onToggle = { onToggleTag(Exclusion.Tag.DEDUPLICATOR) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.swiper_tool_name),
                    checked = ready.current.tags.contains(Exclusion.Tag.SWIPER),
                    onToggle = { onToggleTag(Exclusion.Tag.SWIPER) },
                )
            }
        }
        Text(
            modifier = Modifier.padding(32.dp),
            text = stringResource(R.string.exclusion_editor_segment_explanation),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun TagToggle(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview2
@Composable
private fun SegmentExclusionEditorScreenPreview() {
    PreviewWrapper {
        SegmentExclusionEditorScreen(
            stateSource = MutableStateFlow(
                SegmentExclusionViewModel.State.Ready(
                    current = SegmentExclusion(
                        segments = segs("cache"),
                        allowPartial = true,
                        ignoreCase = true,
                        tags = setOf(Exclusion.Tag.GENERAL),
                    ),
                    canSave = true,
                    canRemove = false,
                ),
            ),
        )
    }
}
