package eu.darken.sdmse.scheduler.core

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
class SchedulerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_scheduler")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val skipWhenPowerSaving = dataStore.createValue("requirement.notpowersaving.enabled", true)
    val skipWhenNotCharging = dataStore.createValue("requirement.charging.enabled", false)
    val useAutomation = dataStore.createValue("option.automation.enabled", false)

    val createdDefaultEntry = dataStore.createValue("default.entry.created", false)

    override val mapper = PreferenceStoreMapper(
        skipWhenPowerSaving,
        skipWhenNotCharging,
        useAutomation
    )

    companion object {
        internal val TAG = logTag("Scheduler", "Settings")
    }
}