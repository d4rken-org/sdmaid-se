package eu.darken.sdmse.common.error

import eu.darken.sdmse.common.SingleLiveEvent

interface ErrorEventSource {
    val errorEvents: SingleLiveEvent<Throwable>
}