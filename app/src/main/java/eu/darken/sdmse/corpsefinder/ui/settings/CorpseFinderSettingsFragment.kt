package eu.darken.sdmse.corpsefinder.ui.settings

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class CorpseFinderSettingsFragment : PreferenceFragment2() {

    private val vm: CorpseFinderSettingsFragmentVM by viewModels()

    @Inject lateinit var corpseFinderSettings: CorpseFinderSettings

    override val settings: CorpseFinderSettings by lazy { corpseFinderSettings }
    override val preferenceFile: Int = R.xml.preferences_corpsefinder


}