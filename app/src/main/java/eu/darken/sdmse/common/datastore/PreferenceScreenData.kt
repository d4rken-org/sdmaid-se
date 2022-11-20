package eu.darken.sdmse.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.preference.PreferenceDataStore

interface PreferenceScreenData {

    val dataStore: DataStore<Preferences>

    val mapper: PreferenceDataStore

}