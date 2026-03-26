package eu.darken.sdmse.corpsefinder.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.CheckBoxPreference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.preferences.BadgedCheckboxPreference
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.corpsefinder.R
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.showSetupHint
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class CorpseFinderSettingsFragment : PreferenceFragment2() {

    private val vm: CorpseFinderSettingsViewModel by viewModels()

    @Inject lateinit var cfSettings: CorpseFinderSettings

    override val settings: CorpseFinderSettings by lazy { cfSettings }
    override val preferenceFile: Int = R.xml.preferences_corpsefinder

    private val filterPrivateDataEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterPrivateDataEnabled.keyName)!!
    private val filterAppSourceAsecEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterAppSourceAsecEnabled.keyName)!!
    private val filterDalvikCacheEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterDalvikCacheEnabled.keyName)!!
    private val filterArtProfilesEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterArtProfilesEnabled.keyName)!!
    private val filterAppLibEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterAppLibEnabled.keyName)!!
    private val filterAppSourceEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterAppSourceEnabled.keyName)!!
    private val filterAppSourcePrivateEnabled: BadgedCheckboxPreference
        get() = findPreference(settings.filterAppSourcePrivateEnabled.keyName)!!

    private val isWatcherEnabled: CheckBoxPreference
        get() = findPreference(settings.isWatcherEnabled.keyName)!!
    private val isWatcherAutoDeleteEnabled: CheckBoxPreference
        get() = findPreference(settings.isWatcherAutoDeleteEnabled.keyName)!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        filterPrivateDataEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterAppSourceAsecEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterDalvikCacheEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterArtProfilesEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterAppLibEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterAppSourceEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
        filterAppSourcePrivateEnabled.badgedAction = { showSetupHint?.invoke(this, setOf(SetupModule.Type.ROOT)) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.state.observe2(this) { state ->
            isWatcherEnabled.apply {
                isPersistent = state.isPro
                if (state.isPro) {
                    setSummary(eu.darken.sdmse.corpsefinder.R.string.corpsefinder_watcher_summary)
                } else {
                    summary =
                        "${getString(eu.darken.sdmse.corpsefinder.R.string.corpsefinder_watcher_summary)}\n${getString(eu.darken.sdmse.common.R.string.upgrade_feature_requires_pro)}"
                }
                setOnPreferenceClickListener {
                    if (!state.isPro) {
                        isChecked = false
                        findNavController().navigate(eu.darken.sdmse.common.navigation.routes.UpgradeRoute())
                        true
                    } else {
                        false
                    }
                }
            }

            isWatcherAutoDeleteEnabled.isEnabled = state.isWatcherEnabled

            filterPrivateDataEnabled.isRestricted = !state.state.isFilterPrivateDataAvailable
            filterAppSourceAsecEnabled.isRestricted = !state.state.isFilterAppSourcesAvailable
            filterDalvikCacheEnabled.isRestricted = !state.state.isFilterDalvikCacheAvailable
            filterArtProfilesEnabled.isRestricted = !state.state.isFilterArtProfilesAvailable
            filterAppLibEnabled.isRestricted = !state.state.isFilterAppLibrariesAvailable
            filterAppSourceEnabled.isRestricted = !state.state.isFilterAppSourcesAvailable
            filterAppSourcePrivateEnabled.isRestricted = !state.state.isFilterPrivateAppSourcesAvailable
        }
    }


}
