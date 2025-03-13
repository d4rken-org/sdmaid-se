package eu.darken.sdmse.appcontrol.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class AppControlSettingsFragment : PreferenceFragment2() {

    private val vm: AppControlSettingsViewModel by viewModels()

    @Inject lateinit var acSettings: AppControlSettings

    override val settings: AppControlSettings by lazy { acSettings }
    override val preferenceFile: Int = R.xml.preferences_appcontrol

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(this) {

        }
        super.onViewCreated(view, savedInstanceState)
    }

}