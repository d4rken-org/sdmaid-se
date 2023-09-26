package eu.darken.sdmse.setup

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.setup.automation.AutomationSetupCardVH
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.notification.NotificationSetupCardVH
import eu.darken.sdmse.setup.notification.NotificationSetupModule
import eu.darken.sdmse.setup.root.RootSetupCardVH
import eu.darken.sdmse.setup.root.RootSetupModule
import eu.darken.sdmse.setup.saf.SAFSetupCardVH
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.shizuku.ShizukuSetupCardVH
import eu.darken.sdmse.setup.shizuku.ShizukuSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupCardVH
import eu.darken.sdmse.setup.storage.StorageSetupModule
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupCardVH
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val setupManager: SetupManager,
    private val storageSetupModule: StorageSetupModule,
    private val safSetupModule: SAFSetupModule,
    private val automationSetupModule: AutomationSetupModule,
    private val webpageTool: WebpageTool,
    private val rootSetupModule: RootSetupModule,
    private val shizukuSetupModule: ShizukuSetupModule,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<SetupFragmentArgs>()

    init {
        setupManager.setDismissed(false)
    }

    // TODO support filtering based on screenOptions.filterTypes
    val screenOptions = navArgs.options ?: SetupScreenOptions()

    val events = SingleLiveEvent<SetupEvents>()

    val listItems: LiveData<List<SetupAdapter.Item>> = setupManager.state
        .map { setupState ->
            val items = mutableListOf<SetupAdapter.Item>()

            setupState.moduleStates
                .filter { screenOptions.typeFilter == null || screenOptions.typeFilter.contains(it.type) }
                .filter { !it.isComplete || screenOptions.showCompleted }
                .mapNotNull { state ->
                    when (state) {
                        is SAFSetupModule.State -> SAFSetupCardVH.Item(
                            state = state,
                            onPathClicked = {
                                if (!it.hasAccess) {
                                    events.postValue(SetupEvents.SafRequestAccess(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#storage-access-framework")
                            },
                        ).takeIf { it.state.paths.isNotEmpty() }

                        is StorageSetupModule.State -> StorageSetupCardVH.Item(
                            state = state,
                            onPathClicked = {
                                state.missingPermission.firstOrNull()?.let {
                                    events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#manage-storage")
                            },
                        )

                        is RootSetupModule.State -> RootSetupCardVH.Item(
                            state = state,
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

                        is UsageStatsSetupModule.State -> UsageStatsSetupCardVH.Item(
                            state = state,
                            onGrantAction = {
                                state.missingPermission.firstOrNull()?.let {
                                    events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#usage-statistics")
                            }
                        )

                        is AutomationSetupModule.State -> AutomationSetupCardVH.Item(
                            state = state,
                            onGrantAction = {
                                launch {
                                    automationSetupModule.setAllow(true)
                                    automationSetupModule.refresh()
                                    if (!state.canSelfEnable) {
                                        events.postValue(SetupEvents.ConfigureAccessibilityService(state))
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
                                events.postValue(SetupEvents.ShowOurDetailsPage(state.liftRestrictionsIntent))
                            },
                            onRestrictionsHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#acs-appops-restrictions")
                            },
                        )

                        is NotificationSetupModule.State -> NotificationSetupCardVH.Item(
                            state = state,
                            onGrantAction = {
                                state.missingPermission.firstOrNull()?.let {
                                    events.postValue(SetupEvents.RuntimePermissionRequests(it))
                                }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#notifications")
                            }
                        )

                        is ShizukuSetupModule.State -> ShizukuSetupCardVH.Item(
                            state = state,
                            onToggleUseShizuku = {
                                launch { shizukuSetupModule.toggleUseShizuku(it) }
                            },
                            onHelp = {
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#shizuku")
                            },
                        )

                        else -> throw IllegalArgumentException("Unknown state: $state")
                    }
                }
                .sortedBy { item ->
                    if (screenOptions.showCompleted && !item.state.isComplete) {
                        Int.MIN_VALUE
                    } else {
                        DISPLAY_ORDER.indexOfFirst { it.isInstance(item) }
                    }
                }
                .run { items.addAll(this) }

            items
        }
        .onEach {
            if (it.isEmpty() && !screenOptions.showCompleted) {
                navback()
            }
        }
        .setupCommonEventHandlers(TAG) { "listItems" }
        .asLiveData2()

    fun onSafAccessGranted(uri: Uri?) = launch {
        log(TAG) { "onSafAccessGranted(uri=$uri)" }
        if (uri == null) return@launch
        try {
            safSetupModule.takePermission(uri)
        } catch (e: IllegalArgumentException) {
            events.postValue(SetupEvents.SafWrongPathError(e))
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

                Permission.IGNORE_BATTERY_OPTIMIZATION -> {}
                Permission.PACKAGE_USAGE_STATS -> {}
                Permission.POST_NOTIFICATIONS -> {}
                Permission.WRITE_SECURE_SETTINGS -> {}
                null -> {}
            }
            setupManager.refresh()
        }
    }

    fun onAccessibilityReturn() = launch {
        log(TAG) { "onAccessibilityReturn" }
        automationSetupModule.refresh()
    }

    fun navback() {
        if (screenOptions.isOnboarding) {
            SetupFragmentDirections.actionSetupFragmentToDashboardFragment().navigate()
        } else {
            popNavStack()
        }
    }

    companion object {
        private val DISPLAY_ORDER = listOf(
            StorageSetupCardVH.Item::class,
            SAFSetupCardVH.Item::class,
            UsageStatsSetupCardVH.Item::class,
            AutomationSetupCardVH.Item::class,
            ShizukuSetupCardVH.Item::class,
            RootSetupCardVH.Item::class,
        )
        private val TAG = logTag("Setup", "ViewModel")
    }
}