package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.filter.CustomFilterEditorRoute
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal sealed interface EditorPendingDialog {
    data object Remove : EditorPendingDialog
    data object UnsavedChanges : EditorPendingDialog
}

/** First-frame guess for the live-search summary row, replaced by the measured height. */
private val LIVESEARCH_SUMMARY_FALLBACK = 56.dp

/**
 * Strictly less than [LiveSearchRow]'s 36dp single-line height (20dp icon + 2x8dp padding), so the
 * hinted match row is always visibly clipped. A fully visible row would read as "that's all of
 * them" instead of "there is more, drag up".
 */
private val LIVESEARCH_MATCH_ROW_PEEK = 24.dp

/**
 * [BottomSheetScaffold] measures the peek band from the physical window bottom and applies no
 * window insets, so the navigation bar inset has to be part of the peek height, as does the drag
 * handle that is rendered above the sheet content.
 */
internal fun liveSearchPeekHeight(
    dragHandleHeight: Dp,
    summaryHeight: Dp,
    hasMatches: Boolean,
    navigationBarBottom: Dp,
    matchRowPeek: Dp = LIVESEARCH_MATCH_ROW_PEEK,
): Dp = dragHandleHeight + summaryHeight + (if (hasMatches) matchRowPeek else 0.dp) + navigationBarBottom

@Composable
fun CustomFilterEditorScreenHost(
    route: CustomFilterEditorRoute,
    vm: CustomFilterEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(route) { vm.bindRoute(route) }

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    var pendingDialog by remember { mutableStateOf<EditorPendingDialog?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            pendingDialog = when (event) {
                is CustomFilterEditorViewModel.Event.RemoveConfirmation -> EditorPendingDialog.Remove
                is CustomFilterEditorViewModel.Event.UnsavedChangesConfirmation -> EditorPendingDialog.UnsavedChanges
            }
        }
    }

    CustomFilterEditorScreen(
        stateSource = vm.state,
        liveSearchSource = vm.liveSearch,
        onClose = vm::cancel,
        onSave = vm::save,
        onRemove = { vm.remove() },
        onLabelChange = vm::updateLabel,
        onAddPath = vm::addPath,
        onRemovePath = vm::removePath,
        onSwapPath = vm::swapPath,
        onAddName = vm::addNameContains,
        onRemoveName = vm::removeNameContains,
        onSwapName = vm::swapNameContains,
        onAddExclusion = vm::addExclusion,
        onRemoveExclusion = vm::removeExclusion,
        onSwapExclusion = vm::swapExclusion,
        onToggleArea = vm::toggleArea,
        onToggleFileType = vm::toggleFileType,
        onUpdateSizeMin = vm::updateSizeMinimum,
        onUpdateSizeMax = vm::updateSizeMaximum,
        onUpdateAgeMin = vm::updateAgeMinimum,
        onUpdateAgeMax = vm::updateAgeMaximum,
    )

    pendingDialog?.let { active ->
        val cancelAction = SdmDialogAction(
            label = stringResource(CommonR.string.general_cancel_action),
            onClick = { pendingDialog = null },
        )
        when (active) {
            EditorPendingDialog.Remove -> SdmConfirmDialog(
                message = stringResource(SystemCleanerR.string.systemcleaner_editor_remove_confirmation_message),
                onDismissRequest = { pendingDialog = null },
                positive = SdmDialogAction(
                    label = stringResource(CommonR.string.general_remove_action),
                    onClick = {
                        pendingDialog = null
                        vm.remove(confirmed = true)
                    },
                ),
                negative = cancelAction,
            )

            EditorPendingDialog.UnsavedChanges -> SdmConfirmDialog(
                message = stringResource(CommonR.string.general_unsaved_confirmation_message),
                onDismissRequest = { pendingDialog = null },
                positive = SdmDialogAction(
                    label = stringResource(CommonR.string.general_discard_action),
                    onClick = {
                        pendingDialog = null
                        vm.cancel(confirmed = true)
                    },
                ),
                negative = cancelAction,
            )
        }
    }
}

@Composable
internal fun CustomFilterEditorScreen(
    stateSource: StateFlow<CustomFilterEditorViewModel.State?> = MutableStateFlow(null),
    liveSearchSource: StateFlow<CustomFilterEditorViewModel.LiveSearchState> =
        MutableStateFlow(CustomFilterEditorViewModel.LiveSearchState(firstInit = true)),
    onClose: () -> Unit = {},
    onSave: () -> Unit = {},
    onRemove: () -> Unit = {},
    onLabelChange: (String) -> Unit = {},
    onAddPath: (eu.darken.sdmse.common.sieve.SegmentCriterium) -> Unit = {},
    onRemovePath: (eu.darken.sdmse.common.sieve.SegmentCriterium) -> Unit = {},
    onSwapPath: (eu.darken.sdmse.common.sieve.SieveCriterium, eu.darken.sdmse.common.sieve.SieveCriterium) -> Unit = { _, _ -> },
    onAddName: (eu.darken.sdmse.common.sieve.NameCriterium) -> Unit = {},
    onRemoveName: (eu.darken.sdmse.common.sieve.NameCriterium) -> Unit = {},
    onSwapName: (eu.darken.sdmse.common.sieve.SieveCriterium, eu.darken.sdmse.common.sieve.SieveCriterium) -> Unit = { _, _ -> },
    onAddExclusion: (eu.darken.sdmse.common.sieve.SegmentCriterium) -> Unit = {},
    onRemoveExclusion: (eu.darken.sdmse.common.sieve.SegmentCriterium) -> Unit = {},
    onSwapExclusion: (eu.darken.sdmse.common.sieve.SieveCriterium, eu.darken.sdmse.common.sieve.SieveCriterium) -> Unit = { _, _ -> },
    onToggleArea: (eu.darken.sdmse.common.areas.DataArea.Type, Boolean) -> Unit = { _, _ -> },
    onToggleFileType: (eu.darken.sdmse.common.files.FileType) -> Unit = {},
    onUpdateSizeMin: (Long?) -> Unit = {},
    onUpdateSizeMax: (Long?) -> Unit = {},
    onUpdateAgeMin: (java.time.Duration?) -> Unit = {},
    onUpdateAgeMax: (java.time.Duration?) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val liveSearch by liveSearchSource.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
        confirmValueChange = { true },
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    val density = LocalDensity.current
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var dragHandleHeight by remember { mutableStateOf(48.dp) }
    var summaryHeight by remember(density.fontScale) {
        mutableStateOf(LIVESEARCH_SUMMARY_FALLBACK * density.fontScale)
    }

    val peekHeight: Dp = liveSearchPeekHeight(
        dragHandleHeight = dragHandleHeight,
        summaryHeight = summaryHeight,
        hasMatches = liveSearch.matches.isNotEmpty(),
        navigationBarBottom = navigationBarBottom,
    )

    val isExpanded by remember { derivedStateOf { sheetState.currentValue == SheetValue.Expanded } }

    BackHandler(enabled = isExpanded) {
        scope.launch { sheetState.partialExpand() }
    }
    BackHandler(enabled = !isExpanded) {
        onClose()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetSwipeEnabled = !liveSearch.firstInit,
        sheetDragHandle = {
            Box(
                modifier = Modifier.onSizeChanged { size ->
                    val measured = with(density) { size.height.toDp() }
                    if (measured != dragHandleHeight) dragHandleHeight = measured
                },
            ) {
                BottomSheetDefaults.DragHandle()
            }
        },
        sheetContent = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                LiveSearchSheetContent(
                    state = liveSearch,
                    modifier = Modifier.heightIn(max = maxHeight * 0.7f),
                    onSummaryHeightChanged = { summaryHeight = it },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(SystemCleanerR.string.systemcleaner_filter_custom_label))
                        state?.current?.label?.takeIf { it.isNotBlank() }?.let { subtitle ->
                            Text(text = subtitle, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.TwoTone.Close,
                        label = stringResource(CommonR.string.general_close_action),
                        onClick = onClose,
                    )
                },
                actions = {
                    val canSave = state?.canSave == true
                    val canRemove = state?.canRemove == true
                    if (canSave) {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Save,
                            label = stringResource(CommonR.string.general_save_action),
                            onClick = onSave,
                        )
                    }
                    if (canRemove) {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.Delete,
                            label = stringResource(CommonR.string.general_remove_action),
                            onClick = onRemove,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val current = state?.current
            if (current == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                CustomFilterEditorBody(
                    config = current,
                    contentBottomPadding = 16.dp,
                    onLabelChange = onLabelChange,
                    onAddPath = onAddPath,
                    onRemovePath = onRemovePath,
                    onSwapPath = onSwapPath,
                    onAddName = onAddName,
                    onRemoveName = onRemoveName,
                    onSwapName = onSwapName,
                    onAddExclusion = onAddExclusion,
                    onRemoveExclusion = onRemoveExclusion,
                    onSwapExclusion = onSwapExclusion,
                    onToggleArea = onToggleArea,
                    onToggleFileType = onToggleFileType,
                    onUpdateSizeMin = onUpdateSizeMin,
                    onUpdateSizeMax = onUpdateSizeMax,
                    onUpdateAgeMin = onUpdateAgeMin,
                    onUpdateAgeMax = onUpdateAgeMax,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun CustomFilterEditorScreenLoadingPreview() {
    PreviewWrapper {
        CustomFilterEditorScreen(stateSource = MutableStateFlow(null))
    }
}

@Preview2
@Composable
private fun CustomFilterEditorScreenPopulatedPreview() {
    PreviewWrapper {
        CustomFilterEditorScreen(
            stateSource = MutableStateFlow(
                CustomFilterEditorViewModel.State(
                    original = null,
                    current = CustomFilterConfig(identifier = "abc", label = "Test"),
                ),
            ),
        )
    }
}
