package eu.darken.sdmse.common.network

import android.net.NetworkRequest
import javax.inject.Inject
import javax.inject.Provider

/**
 * Indirection to allow this to run in unit tests, without requiring framework classes
 */
class NetworkRequestBuilderProvider @Inject constructor() : Provider<NetworkRequest.Builder> {
    override fun get(): NetworkRequest.Builder = NetworkRequest.Builder()
}
