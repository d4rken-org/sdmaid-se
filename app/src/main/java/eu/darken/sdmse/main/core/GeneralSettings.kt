package eu.darken.sdmse.main.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.datastore.PreferenceStoreMapper
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debugSettings: DebugSettings,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_core")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val themeType = dataStore.createValue("core.ui.theme.type", ThemeType.SYSTEM.identifier)

    val isOnboardingCompleted = dataStore.createValue("core.onboarding.completed", false)

    val hasAcsConsent = dataStore.createValue("core.acs.consent", null as Boolean?)

    val isSetupDismissed = dataStore.createValue("core.setup.dismissed", false)

    val isBugReporterEnabled = dataStore.createValue(
        "core.bugreporter.enabled",
        BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.GPLAY
    )

    override val mapper = PreferenceStoreMapper(
        debugSettings.isDebugMode,
        themeType,
        isBugReporterEnabled
    )

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}