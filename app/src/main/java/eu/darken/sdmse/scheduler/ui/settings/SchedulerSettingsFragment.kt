package eu.darken.sdmse.scheduler.ui.settings

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SchedulerSettingsFragment : PreferenceFragment2() {

    private val vm: SchedulerSettingsViewModel by viewModels()

    @Inject lateinit var _settings: SchedulerSettings

    override val settings: SchedulerSettings by lazy { _settings }
    override val preferenceFile: Int = R.xml.preferences_scheduler

}