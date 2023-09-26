package eu.darken.sdmse.systemcleaner.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.preferences.BadgedCheckboxPreference
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.main.ui.settings.SettingsFragmentDirections
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.showFixSetupHint
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SystemCleanerSettingsFragment : PreferenceFragment2() {

    private val vm: SystemCleanerSettingsViewModel by viewModels()

    @Inject lateinit var _settings: SystemCleanerSettings

    override val settings: SystemCleanerSettings by lazy { _settings }
    override val preferenceFile: Int = R.xml.preferences_systemcleaner

    private var isPro: Boolean? = null

    private val customFilterEntry: Preference
        get() = findPreference("filter.custom")!!

    private val filterAnrEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterAnrEnabled.keyName)!!
    private val filterLocalTmpEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterLocalTmpEnabled.keyName)!!
    private val filterDownloadCacheEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterDownloadCacheEnabled.keyName)!!
    private val filterDataLoggerEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterDataLoggerEnabled.keyName)!!
    private val filterLogDropboxEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterLogDropboxEnabled.keyName)!!
    private val filterRecentTasksEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterRecentTasksEnabled.keyName)!!
    private val filterTombstonesEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterTombstonesEnabled.keyName)!!
    private val filterUsageStatsEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterUsageStatsEnabled.keyName)!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        customFilterEntry.setOnPreferenceClickListener {
            SettingsFragmentDirections.actionSettingsContainerFragmentToCustomFilterListFragment().navigate()
            true
        }

        filterAnrEnabled.badgedAction = { listOf(SetupModule.Type.ROOT).showFixSetupHint(this) }
        filterLocalTmpEnabled.badgedAction = { listOf(SetupModule.Type.ROOT).showFixSetupHint(this) }
        filterDownloadCacheEnabled.badgedAction = { listOf(SetupModule.Type.ROOT).showFixSetupHint(this) }
        filterDataLoggerEnabled.badgedAction = { listOf(SetupModule.Type.ROOT).showFixSetupHint(this) }
        filterLogDropboxEnabled.badgedAction = { listOf(SetupModule.Type.ROOT).showFixSetupHint(this) }
        filterRecentTasksEnabled.badgedAction = { listOf(SetupModule.Type.ROOT).showFixSetupHint(this) }
        filterTombstonesEnabled.badgedAction = { listOf(SetupModule.Type.ROOT).showFixSetupHint(this) }
        filterUsageStatsEnabled.badgedAction = { listOf(SetupModule.Type.ROOT).showFixSetupHint(this) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.state.observe2(this) { state ->
            isPro = state.isPro

            filterAnrEnabled.isRestricted = !state.areSystemFilterAvailable
            filterLocalTmpEnabled.isRestricted = !state.areSystemFilterAvailable
            filterDownloadCacheEnabled.isRestricted = !state.areSystemFilterAvailable
            filterDataLoggerEnabled.isRestricted = !state.areSystemFilterAvailable
            filterLogDropboxEnabled.isRestricted = !state.areSystemFilterAvailable
            filterRecentTasksEnabled.isRestricted = !state.areSystemFilterAvailable
            filterTombstonesEnabled.isRestricted = !state.areSystemFilterAvailable
            filterUsageStatsEnabled.isRestricted = !state.areSystemFilterAvailable
        }
    }

}