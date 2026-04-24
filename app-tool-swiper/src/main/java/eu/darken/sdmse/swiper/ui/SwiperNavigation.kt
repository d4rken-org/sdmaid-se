package eu.darken.sdmse.swiper.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.swiper.ui.settings.SwiperSettingsScreenHost
import eu.darken.sdmse.swiper.ui.status.SwiperStatusScreenHost
import javax.inject.Inject

class SwiperNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<SwiperSettingsRoute> { SwiperSettingsScreenHost() }
        entry<SwiperStatusRoute> { SwiperStatusScreenHost() }
    }
}
