package eu.darken.sdmse.corpsefinder.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class CorpseFinderSettingsFragment : PreferenceFragment2() {

    private val vm: CorpseFinderSettingsFragmentVM by viewModels()

    @Inject lateinit var cfSettings: CorpseFinderSettings

    override val settings: CorpseFinderSettings by lazy { cfSettings }
    override val preferenceFile: Int = R.xml.preferences_corpsefinder

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findPreference<Preference>(settings.filterPrivateDataEnabled.keyName)?.apply {
            summary = summary.toString() + "\n" + getString(R.string.general_root_required_message)
        }
        findPreference<Preference>(settings.filterAppSourceAsecEnabled.keyName)?.apply {
            summary = summary.toString() + "\n" + getString(R.string.general_root_required_message)
        }
        findPreference<Preference>(settings.filterDalvikCacheEnabled.keyName)?.apply {
            summary = summary.toString() + "\n" + getString(R.string.general_root_required_message)
        }
        findPreference<Preference>(settings.filterAppLibEnabled.keyName)?.apply {
            summary = summary.toString() + "\n" + getString(R.string.general_root_required_message)
        }
        findPreference<Preference>(settings.filterAppSourceEnabled.keyName)?.apply {
            summary = summary.toString() + "\n" + getString(R.string.general_root_required_message)
        }
        findPreference<Preference>(settings.filterAppSourcePrivateEnabled.keyName)?.apply {
            summary = summary.toString() + "\n" + getString(R.string.general_root_required_message)
        }
        findPreference<Preference>(settings.filterAppToSdEnabled.keyName)?.apply {
            summary = summary.toString() + "\n" + getString(R.string.general_root_required_message)
        }

        vm.state.observe2(this) {
            findPreference<Preference>(settings.filterPrivateDataEnabled.keyName)?.apply {
                isEnabled = it.isRooted
            }
            findPreference<Preference>(settings.filterAppSourceAsecEnabled.keyName)?.apply {
                isEnabled = it.isRooted
            }
            findPreference<Preference>(settings.filterDalvikCacheEnabled.keyName)?.apply {
                isEnabled = it.isRooted
            }
            findPreference<Preference>(settings.filterAppLibEnabled.keyName)?.apply {
                isEnabled = it.isRooted
            }
            findPreference<Preference>(settings.filterAppSourceEnabled.keyName)?.apply {
                isEnabled = it.isRooted
            }
            findPreference<Preference>(settings.filterAppSourcePrivateEnabled.keyName)?.apply {
                isEnabled = it.isRooted
            }
            findPreference<Preference>(settings.filterAppToSdEnabled.keyName)?.apply {
                isEnabled = it.isRooted
            }
        }
    }

}