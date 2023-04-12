package eu.darken.sdmse.main.ui.settings.acks

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class AcknowledgementsFragment : PreferenceFragment2() {

    private val vm: AcknowledgementsFragmentVM by viewModels()

    override val preferenceFile: Int = R.xml.preferences_acknowledgements
    @Inject lateinit var debugSettings: GeneralSettings

    override val settings: GeneralSettings by lazy { debugSettings }

    override fun onPreferencesChanged() {
        super.onPreferencesChanged()
        findPreference<Preference>("translation.translators")!!.apply {
            val peopleRaw = getString(R.string.translation_translators_people)
            summary = try {
                peopleRaw.split(";").joinToString("\n")
            } catch (e: Exception) {
                log(WARN) { "Translator split failed $peopleRaw: ${e.asLog()}" }
                peopleRaw
            }
        }
    }

}