package eu.darken.sdmse.main.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.preferences.Preference2
import eu.darken.sdmse.common.preferences.tintIcon
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
    private val setupPref: Preference
        get() = findPreference("setup.show.forced")!!
    private val changelogPref: Preference2
        get() = findPreference("core.changelog")!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(this) { state ->
            sponsorPref.isVisible = BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.FOSS && !state.isPro
            setupPref.tintIcon(
                requireContext().getColorForAttr(
                    if (state.setupDone) androidx.appcompat.R.attr.colorControlNormal
                    else com.google.android.material.R.attr.colorTertiary
                )
            )
        }

        vm.events.observe2(this) { event ->
            when (event) {
                is SettingEvents.ShowVersionInfo -> Snackbar.make(requireView(), event.info, Snackbar.LENGTH_INDEFINITE)
                    .setAction(eu.darken.sdmse.common.R.string.general_copy_action) { vm.copyVersionInfos() }
                    .setTextMaxLines(20)
                    .show()
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPreferencesCreated() {
        findPreference<Preference>("exclusions.list.show")!!.setOnPreferenceClickListener {
            SettingsFragmentDirections.actionSettingsContainerFragmentToExclusionsListFragment().navigate()
            true
        }

        setupPref.setOnPreferenceClickListener {
            MainDirections.goToSetup(options = SetupScreenOptions(showCompleted = true)).navigate()
            true
        }

        changelogPref.apply {
            summary = BuildConfigWrap.VERSION_DESCRIPTION
            this.setOnLongClickListener {
                vm.showVersionInfos()
                true
            }
        }
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