package eu.darken.sdmse.appcleaner.ui.settings

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.common.uix.PreferenceFragment2
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class AppCleanerSettingsFragment : PreferenceFragment2() {

    private val vm: AppCleanerSettingsFragmentVM by viewModels()

    @Inject override lateinit var settings: AppCleanerSettings
    override val preferenceFile: Int = R.xml.preferences_appcleaner

}