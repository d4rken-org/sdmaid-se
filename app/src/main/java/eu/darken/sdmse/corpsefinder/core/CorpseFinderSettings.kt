package eu.darken.sdmse.corpsefinder.core

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
class CorpseFinderSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_corpsefinder")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val filterAppSourceAsecEnabled = dataStore.createValue("filter.appasec.enabled", false)
    val filterPublicMediaEnabled = dataStore.createValue("filter.publicmedia.enabled", true)
    val filterPublicObbEnabled = dataStore.createValue("filter.publicobb.enabled", false)
    val filterPublicDataEnabled = dataStore.createValue("filter.publicdata.enabled", true)
    val filterSdcardEnabled = dataStore.createValue("filter.sdcard.enabled", true)
    val filterPrivateDataEnabled = dataStore.createValue("filter.privatedata.enabled", true)
    val filterDalvikCacheEnabled = dataStore.createValue("filter.dalvikcache.enabled", false)
    val filterAppLibEnabled = dataStore.createValue("filter.applib.enabled", false)
    val filterAppSourceEnabled = dataStore.createValue("filter.appsource.enabled", false)
    val filterAppSourcePrivateEnabled = dataStore.createValue("filter.appsourceprivate.enabled", false)
    val filterAppToSdEnabled = dataStore.createValue("filter.apptosd.enabled", false)

    val isWatcherEnabled = dataStore.createValue("watcher.uninstall.enabled", false)
    val isWatcherAutoDeleteEnabled = dataStore.createValue("watcher.uninstall.autodelete.enabled", true)

    val includeRiskKeeper = dataStore.createValue("risk.include.keeper", false)
    val includeRiskCommon = dataStore.createValue("risk.include.common", false)

    override val mapper = PreferenceStoreMapper(
        isWatcherEnabled,
        isWatcherAutoDeleteEnabled,
        includeRiskCommon,
        includeRiskKeeper,
        filterPublicMediaEnabled,
        filterPublicObbEnabled,
        filterPublicDataEnabled,
        filterSdcardEnabled,
        filterPrivateDataEnabled,
        filterAppSourceAsecEnabled,
        filterDalvikCacheEnabled,
        filterAppLibEnabled,
        filterAppSourceEnabled,
        filterAppSourcePrivateEnabled,
        filterAppToSdEnabled,
    )

    companion object {
        internal val TAG = logTag("CorpseFinder", "Settings")
    }
}