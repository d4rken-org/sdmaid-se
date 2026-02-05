package eu.darken.sdmse.swiper.ui.settings

import androidx.annotation.Keep
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.swiper.core.SwiperSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SwiperSettingsFragment : PreferenceFragment2() {

    @Inject lateinit var swiperSettings: SwiperSettings

    override val settings: SwiperSettings by lazy { swiperSettings }
    override val preferenceFile: Int = R.xml.preferences_swiper
}
