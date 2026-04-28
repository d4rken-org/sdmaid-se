package eu.darken.sdmse.exclusion.ui.editor.pkg

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.exclusion.R
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.exclusion.ui.PkgExclusionEditorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PkgExclusionEditorScreenHost(
    route: PkgExclusionEditorRoute,
    vm: PkgExclusionViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(Unit) { vm.setArgs(route.exclusionId, route.initial) }

    var pendingRemove by remember { mutableStateOf(false) }
    var pendingCancel by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                PkgExclusionViewModel.Event.RemoveConfirmation -> pendingRemove = true
                PkgExclusionViewModel.Event.UnsavedChangesConfirmation -> pendingCancel = true
            }
        }
    }

    BackHandler { vm.cancel() }

    PkgExclusionEditorScreen(
        stateSource = vm.state,
        onNavigateBack = { vm.cancel() },
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
                }) {
                    Text(stringResource(CommonR.string.general_remove_action))
                }
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
                }) {
                    Text(stringResource(CommonR.string.general_discard_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancel = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PkgExclusionEditorScreen(
    stateSource: StateFlow<PkgExclusionViewModel.State> = MutableStateFlow(PkgExclusionViewModel.State.Loading),
    onNavigateBack: () -> Unit = {},
    onToggleTag: (Exclusion.Tag) -> Unit = {},
    onSave: () -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exclusion_type_package)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val ready = state as? PkgExclusionViewModel.State.Ready
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
            PkgExclusionViewModel.State.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            PkgExclusionViewModel.State.NotFound -> Box(
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

            is PkgExclusionViewModel.State.Ready -> ReadyContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                ready = s,
                onToggleTag = onToggleTag,
            )
        }
    }
}

@Composable
private fun ReadyContent(
    modifier: Modifier = Modifier,
    ready: PkgExclusionViewModel.State.Ready,
    onToggleTag: (Exclusion.Tag) -> Unit,
) {
    val context = LocalContext.current
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ready.pkg?.let {
                        AsyncImage(
                            modifier = Modifier.size(32.dp),
                            model = it,
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
                            text = ready.current.pkgId.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                    label = stringResource(CommonR.string.appcleaner_tool_name),
                    checked = ready.current.tags.contains(Exclusion.Tag.APPCLEANER),
                    onToggle = { onToggleTag(Exclusion.Tag.APPCLEANER) },
                )
            }
        }
        Text(
            modifier = Modifier.padding(32.dp),
            text = stringResource(R.string.exclusion_editor_pkg_explanation),
            style = MaterialTheme.typography.bodyMedium,
        )
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
private fun PkgExclusionEditorScreenPreview() {
    PreviewWrapper {
        PkgExclusionEditorScreen(
            stateSource = MutableStateFlow(
                PkgExclusionViewModel.State.Ready(
                    current = PkgExclusion(
                        pkgId = Pkg.Id("com.example.app"),
                        tags = setOf(Exclusion.Tag.GENERAL),
                    ),
                    pkg = null,
                    canSave = true,
                    canRemove = false,
                ),
            ),
        )
    }
}
