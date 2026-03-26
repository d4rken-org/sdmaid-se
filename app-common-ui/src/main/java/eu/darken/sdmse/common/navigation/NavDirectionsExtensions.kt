package eu.darken.sdmse.common.navigation

import eu.darken.sdmse.common.SingleLiveEvent

interface NavEventSource {
    val navEvents: SingleLiveEvent<in NavCommand>
}
