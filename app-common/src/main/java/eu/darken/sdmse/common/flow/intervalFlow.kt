package eu.darken.sdmse.common.flow

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration

fun intervalFlow(interval: Duration, intitialDelay: Duration = Duration.ZERO) = flow {
    delay(intitialDelay)
    while (currentCoroutineContext().isActive) {
        emit(Unit)
        delay(interval)
    }
}