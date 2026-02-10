package eu.darken.sdmse.automation.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.animation.AnimationState
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_automation")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val animationPendingRestoreState = dataStore.createValue<AnimationState?>("animation.pending.restore.state", null, moshi)

    companion object {
        internal val TAG = logTag("Automation", "Settings")
    }
}
