package eu.darken.sdmse.appcleaner.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCleanerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_appcleaner")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val includeInaccessibleEnabled = dataStore.createValue("include.inaccessible.enabled", true)

    val forceStopBeforeClearing = dataStore.createValue("forcestop.before.clearing.enabled", false)

    val includeSystemAppsEnabled = dataStore.createValue("include.systemapps.enabled", false)
    val includeRunningAppsEnabled = dataStore.createValue("include.runningapps.enabled", true)
    val includeOtherUsersEnabled = dataStore.createValue("include.multiuser.enabled", false)

    val useAccessibilityService = dataStore.createValue("acs.enabled", false)

    val filterDefaultCachesPublicEnabled = dataStore.createValue("filter.defaultcachespublic.enabled", true)
    val filterDefaultCachesPrivateEnabled = dataStore.createValue("filter.defaultcachesprivate.enabled", true)
    val filterCodeCacheEnabled = dataStore.createValue("filter.codecache.enabled", true)
    val filterAdvertisementEnabled = dataStore.createValue("filter.advertisement.enabled", true)
    val filterBugreportingEnabled = dataStore.createValue("filter.bugreporting.enabled", false)
    val filterAnalyticsEnabled = dataStore.createValue("filter.analytics.enabled", true)
    val filterGameFilesEnabled = dataStore.createValue("filter.gamefiles.enabled", false)
    val filterHiddenCachesEnabled = dataStore.createValue("filter.hiddencaches.enabled", true)
    val filterThumbnailsEnabled = dataStore.createValue("filter.thumbnails.enabled", true)
    val filterOfflineCachesEnabled = dataStore.createValue("filter.offlinecache.enabled", false)
    val filterRecycleBinsEnabled = dataStore.createValue("filter.recyclebins.enabled", false)
    val filterShortcutServiceEnabled = dataStore.createValue("filter.shortcutservice.enabled", false)

    val filterWebviewEnabled = dataStore.createValue("filter.webview.enabled", true)
    val filterThreemaEnabled = dataStore.createValue("filter.threema.enabled", false)
    val filterTelegramEnabled = dataStore.createValue("filter.telegram.enabled", false)
    val filterWhatsAppBackupsEnabled = dataStore.createValue("filter.whatsapp.backups.enabled", false)
    val filterWhatsAppReceivedEnabled = dataStore.createValue("filter.whatsapp.received.enabled", false)
    val filterWhatsAppSentEnabled = dataStore.createValue("filter.whatsapp.sent.enabled", false)
    val filterWeChatEnabled = dataStore.createValue("filter.wechat.enabled", false)
    val filterMobileQQEnabled = dataStore.createValue("filter.mobileqq.enabled", false)
    val filterViberEnabled = dataStore.createValue("filter.viber.enabled", false)

    val minCacheAgeMs = dataStore.createValue("skip.mincacheage.milliseconds", MIN_CACHE_AGE_DEFAULT)

    // Amounts to common folders created by default
    val minCacheSizeBytes = dataStore.createValue<Long>("skip.mincachesize.bytes", MIN_CACHE_SIZE_DEFAULT)

    companion object {
        const val MIN_CACHE_SIZE_DEFAULT = 48 * 1024L
        const val MIN_CACHE_AGE_DEFAULT = 0L
        internal val TAG = logTag("AppCleaner", "Settings")
    }
}