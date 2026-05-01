package eu.darken.sdmse.setup

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.setup.automation.AutomationSetupCard
import eu.darken.sdmse.setup.automation.AutomationSetupCardItem
import eu.darken.sdmse.setup.inventory.InventorySetupCard
import eu.darken.sdmse.setup.inventory.InventorySetupCardItem
import eu.darken.sdmse.setup.notification.NotificationSetupCard
import eu.darken.sdmse.setup.notification.NotificationSetupCardItem
import eu.darken.sdmse.setup.root.RootSetupCard
import eu.darken.sdmse.setup.root.RootSetupCardItem
import eu.darken.sdmse.setup.saf.SAFSetupCard
import eu.darken.sdmse.setup.saf.SAFSetupCardItem
import eu.darken.sdmse.setup.shizuku.ShizukuSetupCard
import eu.darken.sdmse.setup.shizuku.ShizukuSetupCardItem
import eu.darken.sdmse.setup.storage.StorageSetupCard
import eu.darken.sdmse.setup.storage.StorageSetupCardItem
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupCard
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupCardItem
import kotlinx.coroutines.launch
import eu.darken.sdmse.common.R as CommonR

private val TAG = logTag("Setup", "Screen")

@Composable
fun SetupScreenHost(
    route: SetupRoute = SetupRoute(),
    vm: SetupViewModel = hiltViewModel(),
) {
    val screenOptions = route.options ?: SetupScreenOptions()
    log(TAG) { "SetupScreenHost route=$route, screenOptions=$screenOptions" }

    LaunchedEffect(screenOptions) {
        vm.setScreenOptions(screenOptions)
    }

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingPermission by remember { mutableStateOf<Permission?>(null) }
    var hasNavigatedBack by rememberSaveable { mutableStateOf(false) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = eu.darken.sdmse.setup.saf.SafGrantPrimaryContract(),
    ) { uri ->
        vm.onSafAccessGranted(uri)
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val perm = pendingPermission
        pendingPermission = null
        vm.onRuntimePermissionsGranted(perm, granted)
    }

    val specialPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val perm = pendingPermission
        pendingPermission = null
        val granted = perm?.isGranted(context) == true
        vm.onRuntimePermissionsGranted(perm, granted)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Covers returns from accessibility settings / app details page (no result callback).
        vm.onAccessibilityReturn()
    }

    val safWrongPathMessage = stringResource(R.string.setup_saf_error_wrong_path)
    val helpActionLabel = stringResource(CommonR.string.general_help_action)

    var showSafMissingAppDialog by remember { mutableStateOf(false) }

    if (showSafMissingAppDialog) {
        AlertDialog(
            onDismissRequest = { showSafMissingAppDialog = false },
            title = { Text(stringResource(CommonR.string.general_error_label)) },
            text = { Text(stringResource(R.string.setup_saf_missing_app_error)) },
            confirmButton = {
                TextButton(onClick = { showSafMissingAppDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        vm.openSafMissingAppHelpUrl()
                        showSafMissingAppDialog = false
                    },
                ) {
                    Text(stringResource(CommonR.string.general_help_action))
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        log(TAG) { "Event collector started" }
        vm.events.collect { event ->
            log(TAG) { "Handling event: $event" }
            when (event) {
                is SetupEvents.SafRequestAccess -> try {
                    safLauncher.launch(event.item)
                } catch (e: ActivityNotFoundException) {
                    log(TAG, WARN) { "SAF picker unavailable: $e" }
                    if (e.message?.contains("OPEN_DOCUMENT_TREE") == true) {
                        showSafMissingAppDialog = true
                    } else {
                        vm.errorEvents.emit(e)
                    }
                }

                is SetupEvents.RuntimePermissionRequests -> {
                    pendingPermission = event.item
                    try {
                        runtimePermissionLauncher.launch(event.item.permissionId)
                    } catch (e: ActivityNotFoundException) {
                        log(TAG, WARN) { "Runtime permission launcher failed: $e" }
                        pendingPermission = null
                        vm.errorEvents.emit(e)
                    }
                }

                is SetupEvents.SpecialPermissionRequest -> {
                    pendingPermission = event.item
                    val error = launchSpecialPermission(specialPermissionLauncher, event.intent, event.fallbackIntent)
                    if (error != null) {
                        pendingPermission = null
                        vm.errorEvents.emit(error)
                    }
                }

                is SetupEvents.ConfigureAccessibilityService -> {
                    startSystemActivity(context, event.item.settingsIntent)?.let {
                        vm.errorEvents.emit(it)
                    }
                }

                is SetupEvents.ShowOurDetailsPage -> {
                    startSystemActivity(context, event.intent)?.let {
                        vm.errorEvents.emit(it)
                    }
                }

                is SetupEvents.SafWrongPathError -> scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = safWrongPathMessage,
                        actionLabel = helpActionLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.openSafWrongPathHelpUrl()
                    }
                }
            }
        }
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState, screenOptions) {
        if (uiState is SetupUiState.Complete &&
            !screenOptions.showCompleted &&
            !hasNavigatedBack
        ) {
            hasNavigatedBack = true
            log(TAG) { "Setup is complete, navigating back." }
            vm.navback()
        }
    }

    SetupScreen(
        uiState = uiState,
        isOnboarding = screenOptions.isOnboarding,
        onBack = vm::navback,
        onHelp = vm::openSetupHelp,
        onShowAreas = vm::navToDataAreas,
        snackbarHostState = snackbarHostState,
    )
}

private fun launchSpecialPermission(
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    intent: Intent,
    fallback: Intent?,
): Throwable? = try {
    launcher.launch(intent)
    null
} catch (e: ActivityNotFoundException) {
    log(TAG, WARN) { "Special permission launcher failed, trying fallback: $e" }
    if (fallback != null) {
        try {
            launcher.launch(fallback)
            null
        } catch (e2: ActivityNotFoundException) {
            log(TAG, WARN) { "Fallback also failed: $e2" }
            e2
        }
    } else {
        e
    }
}

private fun startSystemActivity(context: android.content.Context, intent: Intent): Throwable? {
    val launchIntent = Intent(intent).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    return try {
        context.startActivity(launchIntent)
        null
    } catch (e: ActivityNotFoundException) {
        log(TAG, WARN) { "startActivity failed: $e" }
        e
    }
}

@Composable
internal fun SetupScreen(
    uiState: SetupUiState = SetupUiState.Loading,
    isOnboarding: Boolean = false,
    onBack: () -> Unit = {},
    onHelp: () -> Unit = {},
    onShowAreas: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = isOnboarding, onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        if (isOnboarding) {
                            Icon(
                                imageVector = Icons.TwoTone.Close,
                                contentDescription = null,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = {
                    SetupMenu(
                        showAreas = !isOnboarding,
                        onHelp = onHelp,
                        onShowAreas = onShowAreas,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (uiState) {
            SetupUiState.Loading,
            SetupUiState.Complete,
            -> LoadingContent(paddingValues)

            is SetupUiState.Cards -> CardsContent(paddingValues, uiState.items)
        }
    }
}

@Composable
private fun SetupMenu(
    showAreas: Boolean,
    onHelp: () -> Unit,
    onShowAreas: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.TwoTone.MoreVert,
            contentDescription = null,
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(CommonR.string.general_help_action)) },
            onClick = {
                expanded = false
                onHelp()
            },
        )
        if (showAreas) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.data_areas_label)) },
                onClick = {
                    expanded = false
                    onShowAreas()
                },
            )
        }
    }
}

@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CardsContent(
    paddingValues: PaddingValues,
    items: List<SetupCardItem>,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.state.type }) { item ->
            SetupCardDispatch(item, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SetupCardDispatch(
    item: SetupCardItem,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is SetupLoadingCardItem -> SetupLoadingCard(item, modifier)
        is SAFSetupCardItem -> SAFSetupCard(item, modifier)
        is StorageSetupCardItem -> StorageSetupCard(item, modifier)
        is RootSetupCardItem -> RootSetupCard(item, modifier)
        is ShizukuSetupCardItem -> ShizukuSetupCard(item, modifier)
        is NotificationSetupCardItem -> NotificationSetupCard(item, modifier)
        is UsageStatsSetupCardItem -> UsageStatsSetupCard(item, modifier)
        is AutomationSetupCardItem -> AutomationSetupCard(item, modifier)
        is InventorySetupCardItem -> InventorySetupCard(item, modifier)
        else -> log(TAG, WARN) { "Unhandled card item type: ${item::class.java.simpleName}" }
    }
}

@Preview2
@Composable
private fun SetupScreenLoadingPreview() {
    PreviewWrapper {
        SetupScreen(uiState = SetupUiState.Loading)
    }
}

@Preview2
@Composable
private fun SetupScreenOnboardingPreview() {
    PreviewWrapper {
        SetupScreen(
            uiState = SetupUiState.Loading,
            isOnboarding = true,
        )
    }
}
