package eu.darken.sdmse.main.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.datastore.PreferenceStoreMapper
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.theming.ThemeMode
import eu.darken.sdmse.common.theming.ThemeStyle
import eu.darken.sdmse.main.core.motd.MotdSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    debugSettings: DebugSettings,
    moshi: Moshi,
    motdSettings: MotdSettings,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_core")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val themeMode = dataStore.createValue("core.ui.theme.mode", ThemeMode.SYSTEM, moshi)
    val themeStyle = dataStore.createValue("core.ui.theme.style", ThemeStyle.DEFAULT, moshi)

    val usePreviews = dataStore.createValue("core.ui.previews.enabled", true)

    val isOnboardingCompleted = dataStore.createValue("core.onboarding.completed", false)

    val hasAcsConsent = dataStore.createValue("core.acs.consent", null as Boolean?)

    val isSetupDismissed = dataStore.createValue("core.setup.dismissed", false)

    val hasPassedAppOpsRestrictions = dataStore.createValue("core.appops.restrictions.passed", false)
    val hasTriggeredRestrictions = dataStore.createValue("core.appops.restrictions.triggered", false)

    val enableDashboardOneClick = dataStore.createValue("dashboard.oneclick.enabled", false)

    // Unused at the moment, but we keep this to remember the setting should we add this again in the future
    val isBugReporterEnabled = dataStore.createValue(
        "core.bugreporter.enabled",
        BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.GPLAY
    )

    override val mapper = PreferenceStoreMapper(
        debugSettings.isDebugMode,
        themeMode,
        themeStyle,
        usePreviews,
        isBugReporterEnabled,
        enableDashboardOneClick,
        motdSettings.isMotdEnabled,
    )

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}