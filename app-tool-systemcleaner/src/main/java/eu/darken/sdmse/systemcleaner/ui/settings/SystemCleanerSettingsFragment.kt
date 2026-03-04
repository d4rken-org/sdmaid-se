package eu.darken.sdmse.systemcleaner.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.systemcleaner.R
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.preferences.BadgedCheckboxPreference
import eu.darken.sdmse.common.ui.AgeInputDialog
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.showSetupHint
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import androidx.navigation.fragment.findNavController
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
    private val filterPackageCaches: BadgedCheckboxPreference
        get() = findPreference(settings.filterPackageCacheEnabled.keyName)!!
    private val filterSuperfluosApksEnabled: CheckBoxPreference
        get() = findPreference(settings.filterSuperfluosApksEnabled.keyName)!!
    private val filterSuperfluosApksIncludeSameVersion: CheckBoxPreference
        get() = findPreference(settings.filterSuperfluosApksIncludeSameVersion.keyName)!!
    private val filterScreenshotsEnabled: CheckBoxPreference
        get() = findPreference(settings.filterScreenshotsEnabled.keyName)!!
    private val filterScreenshotsAge: Preference
        get() = findPreference(settings.filterScreenshotsAge.keyName)!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        customFilterEntry.setOnPreferenceClickListener {
            findNavController().navigate(eu.darken.sdmse.common.R.id.goToCustomFilterList)
            true
        }

        filterSuperfluosApksIncludeSameVersion.isVisible = filterSuperfluosApksEnabled.isChecked
        filterSuperfluosApksEnabled.setOnPreferenceChangeListener { _, isEnabled ->
            filterSuperfluosApksIncludeSameVersion.isVisible = isEnabled as Boolean
            true
        }

        filterScreenshotsAge.apply {
            isVisible = filterScreenshotsEnabled.isChecked
            setOnPreferenceClickListener {
                AgeInputDialog(
                    requireActivity(),
                    titleRes = eu.darken.sdmse.systemcleaner.R.string.systemcleaner_filter_screenshots_age_label,
                    currentAge = settings.filterScreenshotsAge.valueBlocking,
                    onReset = {
                        settings.filterScreenshotsAge.valueBlocking = SystemCleanerSettings.SCREENSHOTS_AGE_DEFAULT
                    },
                    onSave = { settings.filterScreenshotsAge.valueBlocking = it }
                ).show()
                true
            }
        }
        filterScreenshotsEnabled.setOnPreferenceChangeListener { _, isEnabled ->
            filterScreenshotsAge.isVisible = isEnabled as Boolean
            true
        }

        filterAnrEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterLocalTmpEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterDownloadCacheEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterDataLoggerEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterLogDropboxEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterRecentTasksEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterTombstonesEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterUsageStatsEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterPackageCaches.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
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
            filterPackageCaches.isRestricted = !state.areSystemFilterAvailable
        }

        vm.screenshotsAge.observe2(this) { age ->
            filterScreenshotsEnabled.summary = getString(
                eu.darken.sdmse.systemcleaner.R.string.systemcleaner_filter_screenshots_summary,
                AgeInputDialog.formatAge(requireContext(), age)
            )
        }
    }

}