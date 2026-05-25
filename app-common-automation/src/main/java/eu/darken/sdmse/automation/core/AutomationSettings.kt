package eu.darken.sdmse.automation.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.animation.AnimationState
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_automation")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val animationPendingRestoreState =
        dataStore.createValue<AnimationState?>("animation.pending.restore.state", null, json)

    /**
     * Build.FINGERPRINT of an OS build on which writing ENABLED_ACCESSIBILITY_SERVICES from our own
     * process is known to be unreliable (silently reverted, or persisted but the service never binds).
     * Empty when the direct write is considered reliable. Keyed to the fingerprint so a ROM update
     * (which changes the fingerprint) automatically re-probes the direct path.
     * Observed on WAIPU TV (Android 14).
     */
    val acsDirectWriteUnreliableFingerprint =
        dataStore.createValue("acs.directwrite.unreliable.fingerprint", "")

    companion object {
        internal val TAG = logTag("Automation", "Settings")
    }
}
