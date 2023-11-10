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
import eu.darken.sdmse.common.updater.UpdateChecker
import eu.darken.sdmse.main.core.motd.MotdSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    debugSettings: DebugSettings,
    moshi: Moshi,
    motdSettings: MotdSettings,
    private val updateChecker: UpdateChecker
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

    val enableDashboardOneClick = dataStore.createValue("dashboard.oneclick.onepass.enabled", false)
    val oneClickCorpseFinderEnabled = dataStore.createValue("dashboard.oneclick.corpsefinder.enabled", true)
    val oneClickSystemCleanerEnabled = dataStore.createValue("dashboard.oneclick.systemcleaner.enabled", true)
    val oneClickAppCleanerEnabled = dataStore.createValue("dashboard.oneclick.appcleaner.enabled", true)
    val oneClickDeduplicatorEnabled = dataStore.createValue("dashboard.oneclick.deduplicator.enabled", false)

    val isUpdateCheckEnabled = dataStore.createValue("updater.check.enabled", updateChecker.isEnabledByDefault())

    override val mapper = PreferenceStoreMapper(
        debugSettings.isDebugMode,
        themeMode,
        themeStyle,
        usePreviews,
        enableDashboardOneClick,
        motdSettings.isMotdEnabled,
        isUpdateCheckEnabled,
    )

    // Unused at the moment, but we keep this to remember the setting should we add this again in the future
    @Suppress("unused")
    val isBugReporterEnabled = dataStore.createValue(
        "core.bugreporter.enabled",
        BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.GPLAY
    )

    companion object {
        internal val TAG = logTag("Core", "Settings")
    }
}