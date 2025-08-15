package eu.darken.sdmse.common.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FossCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    moshi: Moshi
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_foss")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val upgrade = dataStore.createValue<FossUpgrade?>(
        key = "foss.upgrade",
        moshi = moshi,
        defaultValue = null,
    )

}