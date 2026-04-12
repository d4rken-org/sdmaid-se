package eu.darken.sdmse.common.navigation

import eu.darken.sdmse.common.flow.SingleEventFlow

interface NavigationEventSource {
    val navEvents: SingleEventFlow<NavEvent>
}
