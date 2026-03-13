package eu.darken.sdmse.appcleaner.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.preferences.BadgedCheckboxPreference
import eu.darken.sdmse.common.ui.AgeInputDialog
import eu.darken.sdmse.common.ui.SizeInputDialog
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.showFixSetupHint
import java.time.Duration
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class AppCleanerSettingsFragment : PreferenceFragment2() {

    private val vm: AppCleanerSettingsViewModel by viewModels()

    @Inject override lateinit var settings: AppCleanerSettings
    override val preferenceFile: Int = R.xml.preferences_appcleaner

    private val includeOtherUsers: BadgedCheckboxPreference
        get() = findPreference(settings.includeOtherUsersEnabled.keyName)!!

    private val includeRunningApps: BadgedCheckboxPreference
        get() = findPreference(settings.includeRunningAppsEnabled.keyName)!!

    private val includeInaccessibleCaches: BadgedCheckboxPreference
        get() = findPreference(settings.includeInaccessibleEnabled.keyName)!!

    private val forceStopBeforeClearing: BadgedCheckboxPreference
        get() = findPreference(settings.forceStopBeforeClearing.keyName)!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        findPreference<Preference>(settings.minCacheSizeBytes.keyName)?.apply {
            setOnPreferenceClickListener {
                SizeInputDialog(
                    requireActivity(),
                    titleRes = R.string.appcleaner_include_minimumsize_label,
                    currentSize = settings.minCacheSizeBytes.valueBlocking,
                    onReset = { settings.minCacheSizeBytes.valueBlocking = AppCleanerSettings.MIN_CACHE_SIZE_DEFAULT },
                    onSave = { settings.minCacheSizeBytes.valueBlocking = it }
                ).show()
                true
            }
        }

        findPreference<Preference>(settings.minCacheAgeMs.keyName)?.apply {
            setOnPreferenceClickListener {
                AgeInputDialog(
                    requireActivity(),
                    titleRes = R.string.appcleaner_include_minimumage_label,
                    currentAge = Duration.ofMillis(settings.minCacheAgeMs.valueBlocking),
                    maximumAge = Duration.ofDays(182),
                    onReset = { settings.minCacheAgeMs.valueBlocking = AppCleanerSettings.MIN_CACHE_AGE_DEFAULT },
                    onSave = { settings.minCacheAgeMs.valueBlocking = it.toMillis() }
                ).show()
                true
            }
        }

        includeOtherUsers.badgedAction = {
            setOf(SetupModule.Type.ROOT).showFixSetupHint(this)
        }
        includeRunningApps.badgedAction = {
            setOf(SetupModule.Type.USAGE_STATS).showFixSetupHint(this)
        }
        includeInaccessibleCaches.badgedAction = {
            setOf(SetupModule.Type.USAGE_STATS, SetupModule.Type.AUTOMATION).showFixSetupHint(this)
        }
        forceStopBeforeClearing.badgedAction = {
            setOf(SetupModule.Type.USAGE_STATS, SetupModule.Type.AUTOMATION).showFixSetupHint(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.state.observe2(this) { state ->
            includeOtherUsers.isRestricted = !state.isOtherUsersAvailable
            includeRunningApps.isRestricted = !state.isRunningAppsDetectionAvailable
            includeInaccessibleCaches.apply {
                isRestricted = !state.isInaccessibleCacheAvailable
                isVisible = state.isAcsRequired
            }
            forceStopBeforeClearing.apply {
                isRestricted = !state.isInaccessibleCacheAvailable
                isVisible = state.isAcsRequired
            }
        }
    }
}