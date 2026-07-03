package eu.darken.sdmse.exclusion.ui.editor.path

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.icons.Asterisk
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.exclusion.R
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.ui.PathExclusionEditorRoute
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PathExclusionEditorScreenHost(
    route: PathExclusionEditorRoute,
    vm: PathExclusionViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(Unit) { vm.setArgs(route.exclusionId, route.initial) }

    var pendingRemove by remember { mutableStateOf(false) }
    var pendingCancel by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                PathExclusionViewModel.Event.RemoveConfirmation -> pendingRemove = true
                PathExclusionViewModel.Event.UnsavedChangesConfirmation -> pendingCancel = true
            }
        }
    }

    BackHandler { vm.cancel() }

    PathExclusionEditorScreen(
        stateSource = vm.state,
        onNavigateBack = { vm.cancel() },
        onEditPath = vm::editPath,
        onToggleTag = vm::toggleTag,
        onSave = vm::save,
        onRemove = { vm.remove() },
    )

    if (pendingRemove) {
        SdmConfirmDialog(
            message = stringResource(R.string.exclusion_editor_remove_confirmation_message),
            onDismissRequest = { pendingRemove = false },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_remove_action),
                onClick = {
                    pendingRemove = false
                    vm.remove(confirmed = true)
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { pendingRemove = false },
            ),
        )
    }

    if (pendingCancel) {
        SdmConfirmDialog(
            message = stringResource(CommonR.string.general_unsaved_confirmation_message),
            onDismissRequest = { pendingCancel = false },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_discard_action),
                onClick = {
                    pendingCancel = false
                    vm.cancel(confirmed = true)
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { pendingCancel = false },
            ),
        )
    }
}

@Composable
internal fun PathExclusionEditorScreen(
    stateSource: StateFlow<PathExclusionViewModel.State> = MutableStateFlow(PathExclusionViewModel.State.Loading),
    onNavigateBack: () -> Unit = {},
    onEditPath: () -> Unit = {},
    onToggleTag: (Exclusion.Tag) -> Unit = {},
    onSave: () -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    SdmScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exclusion_type_path)) },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateBack,
                    )
                },
                actions = {
                    val ready = state as? PathExclusionViewModel.State.Ready
                    if (ready?.canRemove == true) {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Delete,
                            label = stringResource(CommonR.string.general_remove_action),
                            onClick = onRemove,
                        )
                    }
                    if (ready?.canSave == true) {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Save,
                            label = stringResource(CommonR.string.general_save_action),
                            onClick = onSave,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val s = state) {
            PathExclusionViewModel.State.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            PathExclusionViewModel.State.NotFound -> Box(
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

            is PathExclusionViewModel.State.Ready -> ReadyContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                ready = s,
                onEditPath = onEditPath,
                onToggleTag = onToggleTag,
            )
        }
    }
}

@Composable
private fun ReadyContent(
    modifier: Modifier = Modifier,
    ready: PathExclusionViewModel.State.Ready,
    onEditPath: () -> Unit,
    onToggleTag: (Exclusion.Tag) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ElevatedCard(
            onClick = { onEditPath() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.exclusion_target_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ready.lookup?.let { lookup ->
                        AsyncImage(
                            modifier = Modifier.size(32.dp),
                            model = lookup,
                            contentDescription = null,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = ready.current.label.get(context),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = ready.current.path.pathType.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.exclusion_editor_path_change_action),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
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
                    icon = SdmIcons.Asterisk,
                    checked = ready.current.tags.contains(Exclusion.Tag.GENERAL),
                    onToggle = { onToggleTag(Exclusion.Tag.GENERAL) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.corpsefinder_tool_name),
                    icon = SDMTool.Type.CORPSEFINDER.icon,
                    checked = ready.current.tags.contains(Exclusion.Tag.CORPSEFINDER),
                    onToggle = { onToggleTag(Exclusion.Tag.CORPSEFINDER) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.systemcleaner_tool_name),
                    icon = SDMTool.Type.SYSTEMCLEANER.icon,
                    checked = ready.current.tags.contains(Exclusion.Tag.SYSTEMCLEANER),
                    onToggle = { onToggleTag(Exclusion.Tag.SYSTEMCLEANER) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.appcleaner_tool_name),
                    icon = SDMTool.Type.APPCLEANER.icon,
                    checked = ready.current.tags.contains(Exclusion.Tag.APPCLEANER),
                    onToggle = { onToggleTag(Exclusion.Tag.APPCLEANER) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.deduplicator_tool_name),
                    icon = SDMTool.Type.DEDUPLICATOR.icon,
                    checked = ready.current.tags.contains(Exclusion.Tag.DEDUPLICATOR),
                    onToggle = { onToggleTag(Exclusion.Tag.DEDUPLICATOR) },
                )
                TagToggle(
                    label = stringResource(CommonR.string.swiper_tool_name),
                    icon = SDMTool.Type.SWIPER.icon,
                    checked = ready.current.tags.contains(Exclusion.Tag.SWIPER),
                    onToggle = { onToggleTag(Exclusion.Tag.SWIPER) },
                )
            }
        }
        Text(
            modifier = Modifier.padding(32.dp),
            text = stringResource(R.string.exclusion_editor_path_explanation),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TagToggle(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview2
@Composable
private fun PathExclusionEditorScreenLoadingPreview() {
    PreviewWrapper {
        PathExclusionEditorScreen(
            stateSource = MutableStateFlow(PathExclusionViewModel.State.Loading),
        )
    }
}
