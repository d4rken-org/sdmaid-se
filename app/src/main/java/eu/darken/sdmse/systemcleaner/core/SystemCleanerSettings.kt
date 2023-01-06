package eu.darken.sdmse.systemcleaner.core

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
class SystemCleanerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_systemcleaner")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    // TODO change defaults
    val filterLogFilesEnabled = dataStore.createValue("filter.logfiles.enabled", true)
    val filterAdvertisementsEnabled = dataStore.createValue("filter.advertisements.enabled", true)
    val filterEmptyDirectoriesEnabled = dataStore.createValue("filter.emptydirectories.enabled", true)
    val filterSuperfluosApksEnabled = dataStore.createValue("filter.superfluosapks.enabled", true)
    val filterLostDirEnabled = dataStore.createValue("filter.lostdir.enabled", true)
    val filterLinuxFilesEnabled = dataStore.createValue("filter.linuxfiles.enabled", true)
    val filterMacFilesEnabled = dataStore.createValue("filter.macfiles.enabled", true)
    val filterThumbnailsEnabled = dataStore.createValue("filter.thumbnails.enabled", true)
    val filterTempFilesEnabled = dataStore.createValue("filter.tempfiles.enabled", true)
    val filterAnalyticsEnabled = dataStore.createValue("filter.analytics.enabled", true)
    val filterWindowsFilesEnabled = dataStore.createValue("filter.windowsfiles.enabled", true)

    val filterAnrEnabled = dataStore.createValue("filter.anr.enabled", true)
    val filterLocalTmpEnabled = dataStore.createValue("filter.localtmp.enabled", true)
    val filterDownloadCacheEnabled = dataStore.createValue("filter.downloadcache.enabled", true)
    val filterDataLoggerEnabled = dataStore.createValue("filter.datalogger.enabled", true)
    val filterLogDropboxEnabled = dataStore.createValue("filter.logdropbox.enabled", true)

    override val mapper = PreferenceStoreMapper(
        filterLogFilesEnabled,
        filterAdvertisementsEnabled,
        filterEmptyDirectoriesEnabled,
        filterSuperfluosApksEnabled,
        filterLostDirEnabled,
        filterLinuxFilesEnabled,
        filterMacFilesEnabled,
        filterThumbnailsEnabled,
        filterTempFilesEnabled,
        filterAnalyticsEnabled,
        filterWindowsFilesEnabled,
        filterAnrEnabled,
        filterLocalTmpEnabled,
        filterDownloadCacheEnabled,
        filterDataLoggerEnabled,
        filterLogDropboxEnabled,
    )

    companion object {
        internal val TAG = logTag("SystemCleaner", "Settings")
    }
}