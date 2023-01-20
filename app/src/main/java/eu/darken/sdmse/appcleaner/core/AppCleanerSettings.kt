package eu.darken.sdmse.appcleaner.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.datastore.PreferenceStoreMapper
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AppCleanerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
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

    val minCacheAgeMs = dataStore.createValue("skip.mincacheage.milliseconds", 0L)
    val minCacheSizeBytes = dataStore.createValue("skip.mincachesize.bytes", 0L)


    override val mapper = PreferenceStoreMapper(
        includeSystemAppsEnabled,
        includeRunningAppsEnabled,
        filterDefaultCachesPublicEnabled,
        filterDefaultCachesPrivateEnabled,
        filterCodeCacheEnabled,
        filterAdvertisementEnabled,
        filterBugreportingEnabled,
        filterAnalyticsEnabled,
    )

    companion object {
        internal val TAG = logTag("AppCleaner", "Settings")
    }
}