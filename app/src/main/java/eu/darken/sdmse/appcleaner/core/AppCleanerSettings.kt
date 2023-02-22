package eu.darken.sdmse.appcleaner.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcleaner.core.automation.specs.CustomSpecs
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.datastore.PreferenceStoreMapper
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AppCleanerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_appcleaner")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    // TODO change defaults
    val includeInaccessibleEnabled = dataStore.createValue("include.inaccessible.enabled", true)
    val includeSystemAppsEnabled = dataStore.createValue("include.systemapps.enabled", true)
    val includeRunningAppsEnabled = dataStore.createValue("include.runningapps.enabled", true)

    val useAccessibilityService = dataStore.createValue("acs.enabled", false)
    val useShizuku = dataStore.createValue("shizuku.enabled", false)

    val filterDefaultCachesPublicEnabled = dataStore.createValue("filter.defaultcachespublic.enabled", true)
    val filterDefaultCachesPrivateEnabled = dataStore.createValue("filter.defaultcachesprivate.enabled", true)
    val filterCodeCacheEnabled = dataStore.createValue("filter.codecache.enabled", true)
    val filterAdvertisementEnabled = dataStore.createValue("filter.advertisement.enabled", true)
    val filterBugreportingEnabled = dataStore.createValue("filter.bugreporting.enabled", true)
    val filterAnalyticsEnabled = dataStore.createValue("filter.analytics.enabled", true)
    val filterGameFilesEnabled = dataStore.createValue("filter.gamefiles.enabled", true)
    val filterHiddenCachesEnabled = dataStore.createValue("filter.hiddencaches.enabled", true)
    val filterOfflineCachesEnabled = dataStore.createValue("filter.offlinecache.enabled", true)
    val filterRecycleBinsEnabled = dataStore.createValue("filter.recyclebins.enabled", true)
    val filterWebviewEnabled = dataStore.createValue("filter.webview.enabled", true)
    val filterThreemaEnabled = dataStore.createValue("filter.threema.enabled", true)
    val filterTelegramEnabled = dataStore.createValue("filter.telegram.enabled", true)
    val filterWhatsAppBackupsEnabled = dataStore.createValue("filter.whatsapp.backups.enabled", true)
    val filterWhatsAppReceivedEnabled = dataStore.createValue("filter.whatsapp.received.enabled", true)
    val filterWhatsAppSentEnabled = dataStore.createValue("filter.whatsapp.sent.enabled", true)
    val filterWeChatEnabled = dataStore.createValue("filter.wechat.enabled", true)

    val minCacheAgeMs = dataStore.createValue("skip.mincacheage.milliseconds", 0L)
    val minCacheSizeBytes = dataStore.createValue("skip.mincachesize.bytes", 0L)

    val automationCustomSteps = dataStore.createValue<CustomSpecs.Config?>("automation.custom.config", null, moshi)

    override val mapper = PreferenceStoreMapper(
        includeSystemAppsEnabled,
        includeRunningAppsEnabled,
        filterDefaultCachesPublicEnabled,
        filterDefaultCachesPrivateEnabled,
        filterCodeCacheEnabled,
        filterAdvertisementEnabled,
        filterBugreportingEnabled,
        filterAnalyticsEnabled,
        filterGameFilesEnabled,
        filterHiddenCachesEnabled,
        filterOfflineCachesEnabled,
        filterRecycleBinsEnabled,
        filterWebviewEnabled,
        filterThreemaEnabled,
        filterTelegramEnabled,
        filterWhatsAppBackupsEnabled,
        filterWhatsAppReceivedEnabled,
        filterWhatsAppSentEnabled,
        filterWeChatEnabled,
    )

    companion object {
        internal val TAG = logTag("AppCleaner", "Settings")
    }
}