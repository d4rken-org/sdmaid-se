package eu.darken.sdmse.common.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

fun <T> Flow<T>.benchmark(action: (Double) -> Unit): Flow<T> {
    var itemCount = 0
    val startTime = System.currentTimeMillis()
    var lastLogTime = System.currentTimeMillis()

    return transform { value ->
        itemCount++
        emit(value)

        val currentTime = System.nanoTime()
        val durationSinceLastLog = (currentTime - lastLogTime) / 1000.0
        if (durationSinceLastLog >= 1) {
            val totalDurationSeconds = (currentTime - startTime) / 1000.0
            action(itemCount / totalDurationSeconds)
            lastLogTime = currentTime
        }
    }
}