package eu.darken.sdmse.systemcleaner.ui.settings

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SystemCleanerSettingsFragment : PreferenceFragment2() {

    private val vm: SystemCleanerSettingsFragmentVM by viewModels()

    @Inject lateinit var _settings: SystemCleanerSettings

    override val settings: SystemCleanerSettings by lazy { _settings }
    override val preferenceFile: Int = R.xml.preferences_systemcleaner

}