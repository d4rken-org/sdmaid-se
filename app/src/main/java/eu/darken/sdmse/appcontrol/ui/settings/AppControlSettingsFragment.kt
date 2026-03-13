package eu.darken.sdmse.appcontrol.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.preferences.BadgedCheckboxPreference
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.showFixSetupHint
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class AppControlSettingsFragment : PreferenceFragment2() {

    private val vm: AppControlSettingsViewModel by viewModels()

    @Inject lateinit var acSettings: AppControlSettings

    override val settings: AppControlSettings by lazy { acSettings }
    override val preferenceFile: Int = R.xml.preferences_appcontrol

    private val determineSizes: BadgedCheckboxPreference
        get() = findPreference(settings.moduleSizingEnabled.keyName)!!

    private val determineRunning: BadgedCheckboxPreference
        get() = findPreference(settings.moduleActivityEnabled.keyName)!!

    private val includeOtherUsers: BadgedCheckboxPreference
        get() = findPreference(settings.includeMultiUserEnabled.keyName)!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        determineSizes.badgedAction = {
            setOf(SetupModule.Type.USAGE_STATS).showFixSetupHint(this)
        }
        determineRunning.badgedAction = {
            setOf(SetupModule.Type.USAGE_STATS).showFixSetupHint(this)
        }
        includeOtherUsers.badgedAction = {
            setOf(SetupModule.Type.ROOT, SetupModule.Type.SHIZUKU).showFixSetupHint(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(this) { state ->
            determineSizes.isRestricted = !state.state.canInfoSize
            determineRunning.isRestricted = !state.state.canInfoActive
            includeOtherUsers.apply {
                isPersistent = state.isPro
                if (state.isPro) {
                    setSummary(R.string.appcleaner_include_multiuser_summary)
                } else {
                    summary =
                        "${getString(R.string.appcleaner_include_multiuser_summary)}\n${getString(R.string.upgrade_feature_requires_pro)}"
                }
                setOnPreferenceClickListener {
                    if (!state.isPro) {
                        isChecked = false
                        MainDirections.goToUpgradeFragment().navigate()
                        true
                    } else {
                        false
                    }
                }
                isRestricted = !state.state.canIncludeMultiUser
            }

            super.onViewCreated(view, savedInstanceState)
        }
    }

}