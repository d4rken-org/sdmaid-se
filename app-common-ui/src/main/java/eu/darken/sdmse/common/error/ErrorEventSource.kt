package eu.darken.sdmse.common.error

import eu.darken.sdmse.common.SingleLiveEvent

// FIXME: Remove after Compose rewrite — ViewModel4 uses SingleEventFlow directly
@Deprecated("ViewModel4 uses SingleEventFlow for error events instead")
interface ErrorEventSource {
    val errorEvents: SingleLiveEvent<Throwable>
}
