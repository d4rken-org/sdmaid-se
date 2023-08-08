package eu.darken.sdmse.systemcleaner.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.main.ui.settings.SettingsFragmentDirections
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.state.observe2(this) { state ->
            isPro = state.isPro
        }
    }

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()
        findPreference<Preference>("filter.custom")!!.setOnPreferenceClickListener {
            if (isPro == false) {
                Toast.makeText(requireContext(), R.string.upgrade_feature_requires_pro, Toast.LENGTH_SHORT).show()
            }
            SettingsFragmentDirections.actionSettingsContainerFragmentToCustomFilterListFragment().navigate()
            true
        }
    }

}