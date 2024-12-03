package eu.darken.sdmse.deduplicator.ui.settings

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.ui.settings.SizeInputDialog
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class DeduplicatorSettingsFragment : PreferenceFragment2() {

    private val vm: DeduplicatorSettingsViewModel by viewModels()

    @Inject lateinit var ddSettings: DeduplicatorSettings

    override val settings: DeduplicatorSettings by lazy { ddSettings }
    override val preferenceFile: Int = R.xml.preferences_deduplicator

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        findPreference<Preference>(settings.minSizeBytes.keyName)?.apply {
            setOnPreferenceClickListener {
                SizeInputDialog(
                    requireActivity(),
                    titleRes = R.string.deduplicator_skip_minsize_title,
                    currentSize = settings.minSizeBytes.valueBlocking,
                    onReset = { settings.minSizeBytes.valueBlocking = DeduplicatorSettings.MIN_FILE_SIZE },
                    onSave = { settings.minSizeBytes.valueBlocking = it }
                ).show()
                true
            }
        }
    }

}