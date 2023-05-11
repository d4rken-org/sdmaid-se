package eu.darken.sdmse.main.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject

@AndroidEntryPoint
class SettingsIndexFragment : PreferenceFragment2() {

    @Inject lateinit var generalSettings: GeneralSettings
    override val settings: PreferenceScreenData
        get() = generalSettings
    override val preferenceFile: Int = R.xml.preferences_index

    @Inject lateinit var webpageTool: WebpageTool
    @Inject lateinit var upgradeRepo: UpgradeRepo

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupMenu(R.menu.menu_settings_index) { item ->
            when (item.itemId) {
                R.id.menu_item_twitter -> {
                    webpageTool.open("https://twitter.com/d4rken")
                }
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPreferencesCreated() {
        findPreference<Preference>("exclusions.list.show")!!.setOnPreferenceClickListener {
            SettingsFragmentDirections.actionSettingsContainerFragmentToExclusionsListFragment().navigate()
            true
        }

        findPreference<Preference>("setup.show.forced")!!.setOnPreferenceClickListener {
            SettingsFragmentDirections.actionSettingsContainerFragmentToSetupFragment(showCompleted = true).navigate()
            true
        }
        findPreference<Preference>("areas.current.show")!!.setOnPreferenceClickListener {
            SettingsFragmentDirections.actionSettingsContainerFragmentToDataAreasFragment().navigate()
            true
        }

        findPreference<Preference>("core.changelog")!!.summary = BuildConfigWrap.VERSION_DESCRIPTION
        findPreference<Preference>("core.privacy")!!.setOnPreferenceClickListener {
            webpageTool.open(SdmSeLinks.PRIVACY_POLICY)
            true
        }
        findPreference<Preference>("core.sponsor.development")?.apply {
            isVisible = BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.FOSS
            setOnPreferenceClickListener {
                webpageTool.open(upgradeRepo.mainWebsite)
                true
            }
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