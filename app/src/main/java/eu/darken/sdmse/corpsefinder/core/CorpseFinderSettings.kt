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

    // TODO change defaults
    val filterPublicMediaEnabled = dataStore.createValue("filter.publicmedia.enabled", true)
    val filterPublicObbEnabled = dataStore.createValue("filter.publicobb.enabled", true)
    val filterPublicDataEnabled = dataStore.createValue("filter.publicdata.enabled", true)
    val filterSdcardEnabled = dataStore.createValue("filter.sdcard.enabled", true)
    val filterPrivateDataEnabled = dataStore.createValue("filter.privatedata.enabled", true)
    val filterAppSourceAsecEnabled = dataStore.createValue("filter.appasec.enabled", true)
    val filterDalvikCacheEnabled = dataStore.createValue("filter.dalvikcache.enabled", true)
    val filterAppLibEnabled = dataStore.createValue("filter.applib.enabled", true)
    val filterAppSourceEnabled = dataStore.createValue("filter.appsource.enabled", true)
    val filterAppSourcePrivateEnabled = dataStore.createValue("filter.appsourceprivate.enabled", true)
    val filterAppToSdEnabled = dataStore.createValue("filter.apptosd.enabled", true)

    val isUninstallWatcherEnabled = dataStore.createValue("watcher.uninstall.enabled", true)

    val includeRiskUserGenerated = dataStore.createValue("risk.include.usergenerated", false)
    val includeRiskCommon = dataStore.createValue("risk.include.common", false)

    override val mapper = PreferenceStoreMapper(
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
        isUninstallWatcherEnabled
    )

    companion object {
        internal val TAG = logTag("CorpseFinder", "Settings")
    }
}