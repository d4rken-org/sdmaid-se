package eu.darken.sdmse.main.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.setup.SetupScreenOptions
import javax.inject.Inject

@AndroidEntryPoint
class SettingsIndexFragment : PreferenceFragment2() {

    @Inject lateinit var generalSettings: GeneralSettings
    override val settings: PreferenceScreenData
        get() = generalSettings
    override val preferenceFile: Int = R.xml.preferences_index

    private val vm: SettingsViewModel by viewModels()

    private val sponsorPref: Preference
        get() = findPreference("core.sponsor.development")!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupMenu(R.menu.menu_settings_index) { item ->
            when (item.itemId) {
                R.id.menu_item_twitter -> {
                    vm.openWebsite("https://twitter.com/d4rken")
                }
            }
        }
        vm.state.observe2(this) {
            sponsorPref.isVisible = BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.FOSS && !it.isPro
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPreferencesCreated() {
        findPreference<Preference>("exclusions.list.show")!!.setOnPreferenceClickListener {
            SettingsFragmentDirections.actionSettingsContainerFragmentToExclusionsListFragment().navigate()
            true
        }

        findPreference<Preference>("setup.show.forced")!!.setOnPreferenceClickListener {
            MainDirections.goToSetup(options = SetupScreenOptions(showCompleted = true)).navigate()
            true
        }

        findPreference<Preference>("core.changelog")!!.summary = BuildConfigWrap.VERSION_DESCRIPTION
        findPreference<Preference>("core.privacy")!!.setOnPreferenceClickListener {
            vm.openWebsite(SdmSeLinks.PRIVACY_POLICY)
            true
        }
        sponsorPref.setOnPreferenceClickListener {
            vm.openUpgradeWebsite()
            true
        }

        findPreference<Preference>("history")!!.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setMessage(eu.darken.sdmse.common.R.string.general_todo_msg)
            }.show()
            true
        }
        super.onPreferencesCreated()
    }
}