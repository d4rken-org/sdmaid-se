package eu.darken.sdmse.appcleaner.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppCleanerSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    appCleaner: AppCleaner,
    private val settings: AppCleanerSettings,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private data class IncludeFlags(
        val systemApps: Boolean,
        val multiUser: Boolean,
        val runningApps: Boolean,
        val inaccessible: Boolean,
        val forceStop: Boolean,
        val minCacheSizeBytes: Long,
        val minCacheAgeMs: Long,
    )

    private data class GenericFilters(
        val defaultCachesPublic: Boolean,
        val defaultCachesPrivate: Boolean,
        val hiddenCaches: Boolean,
        val thumbnails: Boolean,
        val codeCache: Boolean,
        val advertisement: Boolean,
        val bugreporting: Boolean,
        val analytics: Boolean,
        val gameFiles: Boolean,
        val offlineCache: Boolean,
        val recycleBins: Boolean,
        val webview: Boolean,
        val shortcutService: Boolean,
    )

    private data class SpecificFilters(
        val whatsappBackups: Boolean,
        val whatsappReceived: Boolean,
        val whatsappSent: Boolean,
        val telegram: Boolean,
        val threema: Boolean,
        val wechat: Boolean,
        val viber: Boolean,
        val mobileqq: Boolean,
    )

    private val includeFlow = combine(
        settings.includeSystemAppsEnabled.flow,
        settings.includeOtherUsersEnabled.flow,
        settings.includeRunningAppsEnabled.flow,
        settings.includeInaccessibleEnabled.flow,
        settings.forceStopBeforeClearing.flow,
        settings.minCacheSizeBytes.flow,
        settings.minCacheAgeMs.flow,
    ) { sys, multi, running, inaccessible, force, size, age ->
        IncludeFlags(sys, multi, running, inaccessible, force, size, age)
    }

    private val genericFilterFlow = combine(
        settings.filterDefaultCachesPublicEnabled.flow,
        settings.filterDefaultCachesPrivateEnabled.flow,
        settings.filterHiddenCachesEnabled.flow,
        settings.filterThumbnailsEnabled.flow,
        settings.filterCodeCacheEnabled.flow,
        settings.filterAdvertisementEnabled.flow,
        settings.filterBugreportingEnabled.flow,
        settings.filterAnalyticsEnabled.flow,
        settings.filterGameFilesEnabled.flow,
        settings.filterOfflineCachesEnabled.flow,
        settings.filterRecycleBinsEnabled.flow,
        settings.filterWebviewEnabled.flow,
        settings.filterShortcutServiceEnabled.flow,
    ) { a, b, c, d, e, f, g, h, i, j, k, l, m ->
        GenericFilters(a, b, c, d, e, f, g, h, i, j, k, l, m)
    }

    private val specificFilterFlow = combine(
        settings.filterWhatsAppBackupsEnabled.flow,
        settings.filterWhatsAppReceivedEnabled.flow,
        settings.filterWhatsAppSentEnabled.flow,
        settings.filterTelegramEnabled.flow,
        settings.filterThreemaEnabled.flow,
        settings.filterWeChatEnabled.flow,
        settings.filterViberEnabled.flow,
        settings.filterMobileQQEnabled.flow,
    ) { wb, wr, ws, tg, tr, wc, vb, qq ->
        SpecificFilters(wb, wr, ws, tg, tr, wc, vb, qq)
    }

    val state: StateFlow<State> = kotlinx.coroutines.flow.combine(
        appCleaner.state,
        includeFlow,
        genericFilterFlow,
        specificFilterFlow,
    ) { appState, include, generic, specific ->
        State(
            isOtherUsersAvailable = appState.isOtherUsersAvailable,
            isRunningAppsDetectionAvailable = appState.isRunningAppsDetectionAvailable,
            isInaccessibleCacheAvailable = appState.isInaccessibleCacheAvailable,
            isAcsRequired = appState.isAcsRequired,
            includeSystemApps = include.systemApps,
            includeOtherUsers = include.multiUser,
            includeRunningApps = include.runningApps,
            includeInaccessible = include.inaccessible,
            forceStopBeforeClearing = include.forceStop,
            minCacheSizeBytes = include.minCacheSizeBytes,
            minCacheAgeMs = include.minCacheAgeMs,
            filterDefaultCachesPublic = generic.defaultCachesPublic,
            filterDefaultCachesPrivate = generic.defaultCachesPrivate,
            filterHiddenCaches = generic.hiddenCaches,
            filterThumbnails = generic.thumbnails,
            filterCodeCache = generic.codeCache,
            filterAdvertisement = generic.advertisement,
            filterBugreporting = generic.bugreporting,
            filterAnalytics = generic.analytics,
            filterGameFiles = generic.gameFiles,
            filterOfflineCache = generic.offlineCache,
            filterRecycleBins = generic.recycleBins,
            filterWebview = generic.webview,
            filterShortcutService = generic.shortcutService,
            filterWhatsappBackups = specific.whatsappBackups,
            filterWhatsappReceived = specific.whatsappReceived,
            filterWhatsappSent = specific.whatsappSent,
            filterTelegram = specific.telegram,
            filterThreema = specific.threema,
            filterWeChat = specific.wechat,
            filterViber = specific.viber,
            filterMobileQQ = specific.mobileqq,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun setIncludeSystemApps(value: Boolean) = launch { settings.includeSystemAppsEnabled.value(value) }
    fun setIncludeOtherUsers(value: Boolean) = launch { settings.includeOtherUsersEnabled.value(value) }
    fun setIncludeRunningApps(value: Boolean) = launch { settings.includeRunningAppsEnabled.value(value) }
    fun setIncludeInaccessible(value: Boolean) = launch { settings.includeInaccessibleEnabled.value(value) }
    fun setForceStopBeforeClearing(value: Boolean) = launch { settings.forceStopBeforeClearing.value(value) }

    fun setMinCacheSizeBytes(value: Long) = launch { settings.minCacheSizeBytes.value(value) }
    fun resetMinCacheSizeBytes() = launch { settings.minCacheSizeBytes.value(AppCleanerSettings.MIN_CACHE_SIZE_DEFAULT) }
    fun setMinCacheAgeMs(value: Long) = launch { settings.minCacheAgeMs.value(value) }
    fun resetMinCacheAgeMs() = launch { settings.minCacheAgeMs.value(AppCleanerSettings.MIN_CACHE_AGE_DEFAULT) }

    fun setFilterDefaultCachesPublic(value: Boolean) = launch { settings.filterDefaultCachesPublicEnabled.value(value) }
    fun setFilterDefaultCachesPrivate(value: Boolean) = launch { settings.filterDefaultCachesPrivateEnabled.value(value) }
    fun setFilterHiddenCaches(value: Boolean) = launch { settings.filterHiddenCachesEnabled.value(value) }
    fun setFilterThumbnails(value: Boolean) = launch { settings.filterThumbnailsEnabled.value(value) }
    fun setFilterCodeCache(value: Boolean) = launch { settings.filterCodeCacheEnabled.value(value) }
    fun setFilterAdvertisement(value: Boolean) = launch { settings.filterAdvertisementEnabled.value(value) }
    fun setFilterBugreporting(value: Boolean) = launch { settings.filterBugreportingEnabled.value(value) }
    fun setFilterAnalytics(value: Boolean) = launch { settings.filterAnalyticsEnabled.value(value) }
    fun setFilterGameFiles(value: Boolean) = launch { settings.filterGameFilesEnabled.value(value) }
    fun setFilterOfflineCache(value: Boolean) = launch { settings.filterOfflineCachesEnabled.value(value) }
    fun setFilterRecycleBins(value: Boolean) = launch { settings.filterRecycleBinsEnabled.value(value) }
    fun setFilterWebview(value: Boolean) = launch { settings.filterWebviewEnabled.value(value) }
    fun setFilterShortcutService(value: Boolean) = launch { settings.filterShortcutServiceEnabled.value(value) }

    fun setFilterWhatsappBackups(value: Boolean) = launch { settings.filterWhatsAppBackupsEnabled.value(value) }
    fun setFilterWhatsappReceived(value: Boolean) = launch { settings.filterWhatsAppReceivedEnabled.value(value) }
    fun setFilterWhatsappSent(value: Boolean) = launch { settings.filterWhatsAppSentEnabled.value(value) }
    fun setFilterTelegram(value: Boolean) = launch { settings.filterTelegramEnabled.value(value) }
    fun setFilterThreema(value: Boolean) = launch { settings.filterThreemaEnabled.value(value) }
    fun setFilterWeChat(value: Boolean) = launch { settings.filterWeChatEnabled.value(value) }
    fun setFilterViber(value: Boolean) = launch { settings.filterViberEnabled.value(value) }
    fun setFilterMobileQQ(value: Boolean) = launch { settings.filterMobileQQEnabled.value(value) }

    fun onOtherUsersBadgeClick() = navTo(setupRoute(setOf(SetupModule.Type.ROOT)))
    fun onRunningAppsBadgeClick() = navTo(setupRoute(setOf(SetupModule.Type.USAGE_STATS)))
    fun onInaccessibleBadgeClick() = navTo(setupRoute(setOf(SetupModule.Type.USAGE_STATS, SetupModule.Type.AUTOMATION)))
    fun onForceStopBadgeClick() = navTo(setupRoute(setOf(SetupModule.Type.USAGE_STATS, SetupModule.Type.AUTOMATION)))

    private fun setupRoute(types: Set<SetupModule.Type>) = SetupRoute(
        options = SetupScreenOptions(showCompleted = true, typeFilter = types),
    )

    data class State(
        val isOtherUsersAvailable: Boolean = false,
        val isRunningAppsDetectionAvailable: Boolean = false,
        val isInaccessibleCacheAvailable: Boolean = false,
        val isAcsRequired: Boolean = false,
        val includeSystemApps: Boolean = false,
        val includeOtherUsers: Boolean = false,
        val includeRunningApps: Boolean = true,
        val includeInaccessible: Boolean = true,
        val forceStopBeforeClearing: Boolean = false,
        val minCacheSizeBytes: Long = AppCleanerSettings.MIN_CACHE_SIZE_DEFAULT,
        val minCacheAgeMs: Long = AppCleanerSettings.MIN_CACHE_AGE_DEFAULT,
        val filterDefaultCachesPublic: Boolean = true,
        val filterDefaultCachesPrivate: Boolean = true,
        val filterHiddenCaches: Boolean = true,
        val filterThumbnails: Boolean = true,
        val filterCodeCache: Boolean = true,
        val filterAdvertisement: Boolean = true,
        val filterBugreporting: Boolean = false,
        val filterAnalytics: Boolean = true,
        val filterGameFiles: Boolean = false,
        val filterOfflineCache: Boolean = false,
        val filterRecycleBins: Boolean = false,
        val filterWebview: Boolean = true,
        val filterShortcutService: Boolean = false,
        val filterWhatsappBackups: Boolean = false,
        val filterWhatsappReceived: Boolean = false,
        val filterWhatsappSent: Boolean = false,
        val filterTelegram: Boolean = false,
        val filterThreema: Boolean = false,
        val filterWeChat: Boolean = false,
        val filterViber: Boolean = false,
        val filterMobileQQ: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Settings", "AppCleaner", "ViewModel")
    }
}
