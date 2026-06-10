package eu.darken.sdmse.swiper.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Add
import eu.darken.sdmse.common.compose.dialog.SdmAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.swiper.core.FileTypeCategory
import eu.darken.sdmse.swiper.core.FileTypeFilter
import eu.darken.sdmse.swiper.core.SortOrder
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.ui.sessions.items.SwiperSessionRow
import eu.darken.sdmse.swiper.ui.sessions.items.SwiperSessionsHeaderCard
import eu.darken.sdmse.swiper.ui.sessions.items.SwiperSessionsUpgradeCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private data class PendingRename(val sessionId: String, val currentLabel: String?)
private data class PendingFilter(val sessionId: String, val current: FileTypeFilter)
private data class PendingSort(val sessionId: String, val current: SortOrder)
private data class PendingScanWarn(val sessionId: String, val sensitivePaths: List<APath>)

@Composable
fun SwiperSessionsScreenHost(
    vm: SwiperSessionsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    SwiperSessionsScreen(
        stateSource = vm.state,
        onNavigateUp = vm::navUp,
        onOpenPicker = vm::openPicker,
        onUpgrade = vm::onUpgradeClick,
        onScan = vm::scanSession,
        onContinue = vm::continueSession,
        onCancelScan = { vm.cancelScan() },
        onDiscard = vm::discardSession,
        onRename = vm::renameSession,
        onUpdateFilter = vm::updateSessionFilter,
        onUpdateSortOrder = vm::updateSessionSortOrder,
    )
}

@Composable
internal fun SwiperSessionsScreen(
    stateSource: StateFlow<SwiperSessionsViewModel.State> = MutableStateFlow(SwiperSessionsViewModel.State()),
    onNavigateUp: () -> Unit = {},
    onOpenPicker: () -> Unit = {},
    onUpgrade: () -> Unit = {},
    onScan: (String) -> Unit = {},
    onContinue: (String) -> Unit = {},
    onCancelScan: () -> Unit = {},
    onDiscard: (String) -> Unit = {},
    onRename: (String, String?) -> Unit = { _, _ -> },
    onUpdateFilter: (String, FileTypeFilter) -> Unit = { _, _ -> },
    onUpdateSortOrder: (String, SortOrder) -> Unit = { _, _ -> },
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingRename by remember { mutableStateOf<PendingRename?>(null) }
    var pendingFilter by remember { mutableStateOf<PendingFilter?>(null) }
    var pendingSort by remember { mutableStateOf<PendingSort?>(null) }
    var pendingDiscard by remember { mutableStateOf<String?>(null) }
    var pendingScanWarn by remember { mutableStateOf<PendingScanWarn?>(null) }
    var showRiskyInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(CommonR.string.swiper_tool_name))
                        if (state.sessionsWithStats.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.swiper_sessions_current_sessions),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
            )
        },
        floatingActionButton = {
            // Free-tier users at the session limit have canCreateNewSession=false; the FAB collapses
            // to icon-only AND must not trigger the picker (legacy disabled the FAB outright).
            ExtendedFloatingActionButton(
                onClick = { if (state.canCreateNewSession) onOpenPicker() },
                icon = { Icon(Icons.TwoTone.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.swiper_select_folders_action)) },
                expanded = state.canCreateNewSession,
                modifier = Modifier.alpha(if (state.canCreateNewSession) 1f else 0.38f),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (state.sessionsWithStats.isEmpty()) {
                item(key = "header") { SwiperSessionsHeaderCard() }
            }
            if (!state.isPro) {
                item(key = "upgrade") {
                    SwiperSessionsUpgradeCard(
                        freeVersionLimit = state.freeVersionLimit,
                        freeSessionLimit = state.freeSessionLimit,
                        onUpgrade = onUpgrade,
                    )
                }
            }
            itemsIndexed(state.sessionsWithStats) { index, entry ->
                val sessionId = entry.session.sessionId
                val displayLabel = entry.session.label
                    ?: context.getString(R.string.swiper_session_default_label, index + 1)
                SwiperSessionRow(
                    sessionWithStats = entry,
                    position = index + 1,
                    isScanning = state.isSessionScanning(sessionId),
                    isCancelling = state.isSessionCancelling(sessionId),
                    isRefreshing = state.isSessionRefreshing(sessionId),
                    isRisky = state.isSessionRisky(sessionId),
                    onScan = {
                        if (state.isSessionRisky(sessionId)) {
                            pendingScanWarn = PendingScanWarn(sessionId, state.riskySessionPaths[sessionId].orEmpty())
                        } else {
                            onScan(sessionId)
                        }
                    },
                    onContinue = { onContinue(sessionId) },
                    onRemove = { pendingDiscard = sessionId },
                    onRename = { pendingRename = PendingRename(sessionId, entry.session.label ?: displayLabel) },
                    onCancel = onCancelScan,
                    onFilter = { pendingFilter = PendingFilter(sessionId, entry.session.fileTypeFilter) },
                    onSortOrder = { pendingSort = PendingSort(sessionId, entry.session.sortOrder) },
                    onRiskyInfo = { showRiskyInfo = true },
                )
            }
        }
    }

    pendingRename?.let { req ->
        RenameSessionDialog(
            current = req.currentLabel,
            onDismiss = { pendingRename = null },
            onSave = { newLabel ->
                onRename(req.sessionId, newLabel)
                pendingRename = null
            },
        )
    }

    pendingFilter?.let { req ->
        FileTypeFilterDialog(
            current = req.current,
            onDismiss = { pendingFilter = null },
            onApply = { filter ->
                onUpdateFilter(req.sessionId, filter)
                pendingFilter = null
            },
        )
    }

    pendingSort?.let { req ->
        SortOrderDialog(
            current = req.current,
            onDismiss = { pendingSort = null },
            onSelect = { sort ->
                onUpdateSortOrder(req.sessionId, sort)
                pendingSort = null
            },
        )
    }

    pendingScanWarn?.let { req ->
        // Name the actual sensitive storage path(s), not the session label — the warning string's
        // %1$s placeholder is for the risky location (legacy parity).
        val pathsLabel = req.sensitivePaths.joinToString("\n") { it.userReadablePath.get(context) }
        SdmConfirmDialog(
            title = stringResource(R.string.swiper_sensitive_root_warning_title),
            message = stringResource(
                R.string.swiper_sensitive_root_warning_message,
                pathsLabel,
            ),
            onDismissRequest = { pendingScanWarn = null },
            positive = SdmDialogAction(
                label = stringResource(R.string.swiper_sensitive_root_warning_continue_action),
                onClick = {
                    pendingScanWarn = null
                    onScan(req.sessionId)
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { pendingScanWarn = null },
            ),
        )
    }

    pendingDiscard?.let { sessionId ->
        SdmConfirmDialog(
            title = stringResource(R.string.swiper_discard_session_confirmation_title),
            message = stringResource(R.string.swiper_discard_session_confirmation_message),
            onDismissRequest = { pendingDiscard = null },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_remove_action),
                onClick = {
                    pendingDiscard = null
                    onDiscard(sessionId)
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = { pendingDiscard = null },
            ),
        )
    }

    if (showRiskyInfo) {
        SdmConfirmDialog(
            title = stringResource(R.string.swiper_session_risky_label),
            message = stringResource(R.string.swiper_session_risky_info_message),
            onDismissRequest = { showRiskyInfo = false },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_close_action),
                onClick = { showRiskyInfo = false },
            ),
        )
    }
}

// itemsIndexed helper that works with LazyListScope. Bypass import clash with the extension import.
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<Swiper.SessionWithStats>,
    itemContent: @Composable (index: Int, item: Swiper.SessionWithStats) -> Unit,
) {
    items(items.size, key = { items[it].session.sessionId }) { index ->
        itemContent(index, items[index])
    }
}

@Composable
private fun RenameSessionDialog(
    current: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var text by remember { mutableStateOf(current ?: "") }
    val isChanged = text.trim() != (current ?: "")
    val isNotEmpty = text.isNotBlank()
    SdmAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.swiper_session_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.swiper_session_rename_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim().takeIf { it.isNotBlank() }) },
                enabled = isChanged && isNotEmpty,
            ) {
                Text(stringResource(CommonR.string.general_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}

@Composable
private fun FileTypeFilterDialog(
    current: FileTypeFilter,
    onDismiss: () -> Unit,
    onApply: (FileTypeFilter) -> Unit,
) {
    var images by remember { mutableStateOf(FileTypeCategory.IMAGES in current.categories) }
    var videos by remember { mutableStateOf(FileTypeCategory.VIDEOS in current.categories) }
    var audio by remember { mutableStateOf(FileTypeCategory.AUDIO in current.categories) }
    var documents by remember { mutableStateOf(FileTypeCategory.DOCUMENTS in current.categories) }
    var archives by remember { mutableStateOf(FileTypeCategory.ARCHIVES in current.categories) }
    var customExtensions by remember {
        mutableStateOf(current.customExtensions.sorted().joinToString(", "))
    }

    SdmAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.swiper_file_type_filter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.swiper_file_type_filter_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CategoryRow(R.string.swiper_file_type_category_images, R.string.swiper_file_type_category_images_subtitle, images) { images = it }
                CategoryRow(R.string.swiper_file_type_category_videos, R.string.swiper_file_type_category_videos_subtitle, videos) { videos = it }
                CategoryRow(R.string.swiper_file_type_category_audio, R.string.swiper_file_type_category_audio_subtitle, audio) { audio = it }
                CategoryRow(R.string.swiper_file_type_category_documents, R.string.swiper_file_type_category_documents_subtitle, documents) { documents = it }
                CategoryRow(R.string.swiper_file_type_category_archives, R.string.swiper_file_type_category_archives_subtitle, archives) { archives = it }
                OutlinedTextField(
                    value = customExtensions,
                    onValueChange = { customExtensions = it },
                    placeholder = { Text("apk, apkm, apks") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val categories = buildSet {
                    if (images) add(FileTypeCategory.IMAGES)
                    if (videos) add(FileTypeCategory.VIDEOS)
                    if (audio) add(FileTypeCategory.AUDIO)
                    if (documents) add(FileTypeCategory.DOCUMENTS)
                    if (archives) add(FileTypeCategory.ARCHIVES)
                }
                onApply(FileTypeFilter(categories, FileTypeFilter.parseCustomExtensions(customExtensions)))
            }) {
                Text(stringResource(R.string.swiper_file_type_filter_apply_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}

@Composable
private fun CategoryRow(
    labelRes: Int,
    subtitleRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, onClick = { onCheckedChange(!checked) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SortOrderDialog(
    current: SortOrder,
    onDismiss: () -> Unit,
    onSelect: (SortOrder) -> Unit,
) {
    val context = LocalContext.current
    SdmAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.general_sort_by_title)) },
        text = {
            Column {
                SortOrder.entries.forEach { order ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = order == current,
                                onClick = { onSelect(order) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = order == current,
                            onClick = { onSelect(order) },
                        )
                        Text(
                            text = order.label.get(context),
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
private fun SwiperSessionsScreenEmptyPreview() {
    PreviewWrapper {
        SwiperSessionsScreen(
            stateSource = MutableStateFlow(SwiperSessionsViewModel.State()),
        )
    }
}
