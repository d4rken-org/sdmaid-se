package eu.darken.sdmse.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.navigation.routes.DataAreasRoute
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.permissions.RuntimePermission
import eu.darken.sdmse.common.permissions.Specialpermission
import eu.darken.sdmse.common.pkgs.getLaunchIntent
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.setup.automation.AutomationSetupCardItem
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.inventory.InventorySetupCardItem
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.notification.NotificationSetupCardItem
import eu.darken.sdmse.setup.notification.NotificationSetupModule
import eu.darken.sdmse.setup.root.RootSetupCardItem
import eu.darken.sdmse.setup.root.RootSetupModule
import eu.darken.sdmse.setup.saf.SAFSetupCardItem
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.shizuku.ShizukuSetupCardItem
import eu.darken.sdmse.setup.shizuku.ShizukuSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupCardItem
import eu.darken.sdmse.setup.storage.StorageSetupModule
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupCardItem
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @Suppress("StaticFieldLeak") @ApplicationContext private val context: Context,
    private val setupManager: SetupManager,
    private val storageSetupModule: StorageSetupModule,
    private val safSetupModule: SAFSetupModule,
    private val automationSetupModule: AutomationSetupModule,
    private val webpageTool: WebpageTool,
    private val rootSetupModule: RootSetupModule,
    private val shizukuSetupModule: ShizukuSetupModule,
    private val deviceDetective: DeviceDetective,
) : ViewModel4(dispatcherProvider, TAG) {

    private val route = SetupRoute.from(handle)

    private val screenOptionsFlow: MutableStateFlow<SetupScreenOptions> =
        MutableStateFlow(route.options ?: SetupScreenOptions())
    val screenOptions: SetupScreenOptions get() = screenOptionsFlow.value

    fun setScreenOptions(options: SetupScreenOptions) {
        log(TAG) { "setScreenOptions($options)" }
        screenOptionsFlow.value = options
    }

    init {
        log(TAG) { "Setup route parsed: options=${route.options}" }
        setupManager.setDismissed(false)
    }

    val events = SingleEventFlow<SetupEvents>()

    private fun emitPermissionEvent(perm: Permission) {
        when (perm) {
            is Specialpermission -> events.tryEmit(
                SetupEvents.SpecialPermissionRequest(
                    item = perm,
                    intent = perm.createIntent(context, deviceDetective),
                    fallbackIntent = perm.createIntentFallback(context),
                )
            )

            is RuntimePermission -> events.tryEmit(SetupEvents.RuntimePermissionRequests(perm))
            else -> log(TAG, WARN) { "Unknown permission type for $perm, cannot launch" }
        }
    }

    private val itemsStateFlow: StateFlow<List<SetupCardItem>?> = combine(
        setupManager.state,
        screenOptionsFlow,
    ) { setupState, options ->
        val items = mutableListOf<SetupCardItem>()

        val typeFilter = options.typeFilter
        setupState.moduleStates
            .filter { typeFilter == null || typeFilter.contains(it.type) }
            .filter { it !is SetupModule.State.Current || !it.isComplete || options.showCompleted }
                .mapNotNull { state ->
                    when (state.type) {
                        SetupModule.Type.SAF -> when (state) {
                            is SetupModule.State.Current -> SAFSetupCardItem(
                                state = state as SAFSetupModule.Result,
                                onPathClicked = {
                                    if (!it.hasAccess) {
                                        events.tryEmit(SetupEvents.SafRequestAccess(it))
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#storage-access-framework")
                                },
                            ).takeIf { it.state.paths.isNotEmpty() }

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.STORAGE -> when (state) {
                            is SetupModule.State.Current -> StorageSetupCardItem(
                                state = state as StorageSetupModule.Result,
                                onPathClicked = {
                                    state.missingPermission.firstOrNull()?.let { perm ->
                                        emitPermissionEvent(perm)
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#manage-storage")
                                },
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.ROOT -> when (state) {
                            is SetupModule.State.Current -> RootSetupCardItem(
                                state = state as RootSetupModule.Result,
                                onToggleUseRoot = {
                                    launch {
                                        rootSetupModule.toggleUseRoot(it)
                                        rootSetupModule.refresh()
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#root-access")
                                },
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.USAGE_STATS -> when (state) {
                            is SetupModule.State.Current -> UsageStatsSetupCardItem(
                                state = state as UsageStatsSetupModule.Result,
                                onGrantAction = {
                                    state.missingPermission.firstOrNull()?.let { perm ->
                                        emitPermissionEvent(perm)
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#usage-statistics")
                                }
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.AUTOMATION -> when (state) {
                            is SetupModule.State.Current -> AutomationSetupCardItem(
                                state = state as AutomationSetupModule.Result,
                                onGrantAction = {
                                    launch {
                                        automationSetupModule.setAllow(true)
                                        automationSetupModule.refresh()
                                        if (!state.canSelfEnable) {
                                            events.tryEmit(SetupEvents.ConfigureAccessibilityService(state))
                                        }
                                    }
                                },
                                onDismiss = {
                                    launch {
                                        automationSetupModule.setAllow(false)
                                        automationSetupModule.refresh()
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#accessibility-service")
                                },
                                onRestrictionsShow = {
                                    events.tryEmit(SetupEvents.ShowOurDetailsPage(state.liftRestrictionsIntent))
                                },
                                onRestrictionsHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#acs-appops-restrictions")
                                },
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.NOTIFICATION -> when (state) {
                            is SetupModule.State.Current -> NotificationSetupCardItem(
                                state = state as NotificationSetupModule.Result,
                                onGrantAction = {
                                    state.missingPermission.firstOrNull()?.let { perm ->
                                        emitPermissionEvent(perm)
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#notifications")
                                }
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.SHIZUKU -> when (state) {
                            is SetupModule.State.Current -> ShizukuSetupCardItem(
                                state = state as ShizukuSetupModule.Result,
                                onToggleUseShizuku = {
                                    launch { shizukuSetupModule.toggleUseShizuku(it) }
                                },
                                onHelp = {
                                    webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#shizuku")
                                },
                                onOpen = {
                                    state.pkg.getLaunchIntent(context)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                                        try {
                                            context.startActivity(it)
                                        } catch (e: ActivityNotFoundException) {
                                            errorEvents.tryEmit(e)
                                        }
                                    }
                                }
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.INVENTORY -> when (state) {
                            is SetupModule.State.Current -> InventorySetupCardItem(
                                state = state as InventorySetupModule.Result,
                                onGrantAction = {
                                    val result = state
                                    val runtimePerm = result.missingPermission.firstOrNull { it is RuntimePermission }
                                    if (runtimePerm != null) {
                                        emitPermissionEvent(runtimePerm)
                                    } else {
                                        events.tryEmit(SetupEvents.ShowOurDetailsPage(result.settingsIntent))
                                    }
                                },
                                onHelp = {
                                    webpageTool.open(InventorySetupModule.INFO_URL)
                                }
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }
                    }
                }
                .sortedBy { item ->
                    if (options.showCompleted && item.state is SetupModule.State.Current && !item.state.isComplete) {
                        Int.MIN_VALUE
                    } else if (item is RootSetupCardItem && item.state.isInstalled && item.state.useRoot == null) {
                        Int.MIN_VALUE
                    } else {
                        DISPLAY_ORDER.indexOfFirst { it == item.state.type }
                    }
                }
                .run { items.addAll(this) }

        items
    }
        .setupCommonEventHandlers(TAG) { "listItems" }
        .stateIn(vmScope, SharingStarted.Eagerly, null)

    val listItems: StateFlow<List<SetupCardItem>> = itemsStateFlow
        .filterNotNull()
        .safeStateIn(
            initialValue = emptyList(),
            onError = { emptyList() },
        )

    val isSetupComplete: StateFlow<Boolean> = combine(
        itemsStateFlow,
        screenOptionsFlow,
    ) { items, options ->
        items != null && items.isEmpty() && !options.showCompleted
    }
        .distinctUntilChanged()
        .safeStateIn(
            initialValue = false,
            onError = { false },
        )

    internal val uiState: StateFlow<SetupUiState> = combine(
        itemsStateFlow,
        screenOptionsFlow,
    ) { items, options ->
            when {
                items == null -> SetupUiState.Loading
                items.isEmpty() && !options.showCompleted -> SetupUiState.Complete
                else -> SetupUiState.Cards(items)
            }
        }
        .distinctUntilChanged()
        .safeStateIn(
            initialValue = SetupUiState.Loading,
            onError = { SetupUiState.Loading },
        )

    fun onSafAccessGranted(uri: Uri?) = launch {
        log(TAG) { "onSafAccessGranted(uri=$uri)" }
        if (uri == null) return@launch
        try {
            safSetupModule.takePermission(uri)
        } catch (e: IllegalArgumentException) {
            events.tryEmit(SetupEvents.SafWrongPathError(e))
        }
    }

    fun onRuntimePermissionsGranted(permission: Permission?, granted: Boolean) = launch {
        log(TAG) { "onRuntimePermissionGranted(result=$permission,granted=$granted)" }
        if (granted) {
            when (permission) {
                Permission.MANAGE_EXTERNAL_STORAGE,
                Permission.READ_EXTERNAL_STORAGE,
                Permission.WRITE_EXTERNAL_STORAGE -> {
                    storageSetupModule.onPermissionChanged(permission, granted)
                }

                Permission.PACKAGE_USAGE_STATS -> {}
                Permission.POST_NOTIFICATIONS -> {}
                Permission.WRITE_SECURE_SETTINGS -> {}
                Permission.QUERY_ALL_PACKAGES -> {}
                Permission.GET_INSTALLED_APPS -> {}
                null -> {}
            }
            setupManager.refresh()
        } else if (permission == Permission.GET_INSTALLED_APPS) {
            // Runtime dialog may not work on some OEMs — fall back to app settings
            val settingsIntent = context.packageName.toPkgId().getSettingsIntent(context)
            events.tryEmit(SetupEvents.ShowOurDetailsPage(settingsIntent))
        }
    }

    fun onAccessibilityReturn() = launch {
        log(TAG) { "onAccessibilityReturn" }
        setupManager.refresh()
    }

    fun openSetupHelp() {
        webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup")
    }

    fun openSafMissingAppHelpUrl() {
        webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#open_document_tree-activitynotfoundexception")
    }

    fun openSafWrongPathHelpUrl() {
        webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#storage-access-framework")
    }

    fun navToDataAreas() {
        navTo(DataAreasRoute)
    }

    fun navback() {
        if (screenOptions.isOnboarding) {
            navTo(
                DashboardRoute,
                popUpTo = DashboardRoute,
                inclusive = true,
            )
        } else {
            navUp()
        }
    }

    companion object {
        private val DISPLAY_ORDER = listOf(
            SetupModule.Type.INVENTORY,
            SetupModule.Type.NOTIFICATION,
            SetupModule.Type.STORAGE,
            SetupModule.Type.SAF,
            SetupModule.Type.SHIZUKU,
            SetupModule.Type.ROOT,
            SetupModule.Type.USAGE_STATS,
            SetupModule.Type.AUTOMATION,
        )
        private val TAG = logTag("Setup", "ViewModel")
    }
}
