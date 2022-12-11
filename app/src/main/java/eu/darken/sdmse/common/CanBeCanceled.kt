package eu.darken.sdmse.common

interface CanBeCanceled {
    suspend fun cancel()
}