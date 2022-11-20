package eu.darken.sdmse.common.network

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.ConnectivityManagerCompat
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.hasApiLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkStateProvider @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val networkRequestBuilderProvider: NetworkRequestBuilderProvider,
    private val manager: ConnectivityManager,
) {

    val networkState: Flow<State> = callbackFlow {
        send(currentState)

        var registeredCallback: ConnectivityManager.NetworkCallback? = null

        fun callbackRefresh(delayValue: Long = 0) {
            appScope.launch {
                delay(delayValue)
                send(currentState)
            }
        }

        try {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    log(TAG, VERBOSE) { "onAvailable(network=$network)" }
                    callbackRefresh()
                }

                /**
                 * Some devices don't update the active network fast enough.
                 * 200ms gave good results on a Pixel 2.
                 */
                override fun onLost(network: Network) {
                    log(TAG, VERBOSE) { "onLost(network=$network)" }
                    callbackRefresh(200)
                }

                /**
                 * Not consistently called in all Android versions
                 * https://issuetracker.google.com/issues/144891976
                 */
                override fun onUnavailable() {
                    log(TAG, VERBOSE) { "onUnavailable()" }
                    callbackRefresh()
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    log(TAG, VERBOSE) { "onLosing(network=$network, maxMsToLive=$maxMsToLive)" }
                    callbackRefresh()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    log(TAG, VERBOSE) { "onCapabilitiesChanged(network=$network, capabilities=$networkCapabilities)" }
                    callbackRefresh()
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    log(TAG, VERBOSE) { "onLinkPropertiesChanged(network=$network, linkProperties=$linkProperties)" }
                    callbackRefresh()
                }

                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                    log(TAG, VERBOSE) { "onBlockedStatusChanged(network=$network, blocked=$blocked)" }
                    callbackRefresh()
                }
            }

            val request = networkRequestBuilderProvider.get()
                .addCapability(NET_CAPABILITY_INTERNET)
                .build()

            /**
             * This may throw java.lang.SecurityException on Samsung devices
             * java.lang.SecurityException:
             *  at android.os.Parcel.createExceptionOrNull (Parcel.java:2385)
             *  at android.net.ConnectivityManager.registerNetworkCallback (ConnectivityManager.java:4564)
             */
            manager.registerNetworkCallback(request, callback)
            registeredCallback = callback
        } catch (e: SecurityException) {
            log(TAG, ERROR) {
                "registerNetworkCallback() threw an undocumented SecurityException, Just Samsung Things™️:${e.asLog()}"
            }
            send(State.Fallback)
        }

        awaitClose {
            log(TAG, VERBOSE) { "unregisterNetworkCallback($registeredCallback)" }
            registeredCallback?.let { manager.unregisterNetworkCallback(it) }
        }
    }
        .distinctUntilChanged()
        .replayingShare(scope = appScope)

    private val currentState: State
        @SuppressLint("NewApi")
        get() = try {
            when {
                hasApiLevel(Build.VERSION_CODES.M) -> modernNetworkState()
                else -> legacyNetworkState()
            }
        } catch (e: Exception) {
            if (BuildConfigWrap.DEBUG) throw e
            // Don't crash on appScope in prod
            log(TAG, ERROR) { "Failed to determine current network state, using fallback:${e.asLog()}" }
            State.Fallback
        }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun modernNetworkState(): State = manager.activeNetwork.let { network ->
        State.Modern(
            activeNetwork = network,
            capabilities = network?.let {
                try {
                    manager.getNetworkCapabilities(it)
                } catch (e: SecurityException) {
                    log(TAG, ERROR) { "Failed to determine network capabilities:${e.asLog()}" }
                    null
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun legacyNetworkState(): State = State.LegacyAPI21(
        isInternetAvailable = manager.activeNetworkInfo?.isConnected ?: false,
        isMeteredConnection = ConnectivityManagerCompat.isActiveNetworkMetered(manager)
    )

    interface State {
        val isMeteredConnection: Boolean
        val isInternetAvailable: Boolean

        data class LegacyAPI21(
            override val isMeteredConnection: Boolean,
            override val isInternetAvailable: Boolean
        ) : State

        data class Modern(
            val activeNetwork: Network?,
            val capabilities: NetworkCapabilities?,
        ) : State {
            override val isInternetAvailable: Boolean
                get() = capabilities?.hasCapability(NET_CAPABILITY_VALIDATED) ?: false

            override val isMeteredConnection: Boolean
                get() {
                    val unMetered = if (hasApiLevel(Build.VERSION_CODES.N)) {
                        capabilities?.hasCapability(NET_CAPABILITY_NOT_METERED) ?: false
                    } else {
                        capabilities?.hasTransport(TRANSPORT_WIFI) ?: false
                    }
                    return !unMetered
                }
        }

        object Fallback : State {
            override val isMeteredConnection: Boolean = true
            override val isInternetAvailable: Boolean = true
        }
    }

    companion object {
        private val TAG = logTag("NetworkStateProvider")
    }
}
