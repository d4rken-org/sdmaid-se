package eu.darken.sdmse.common.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.permissions.hasPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WifiStateProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val networkRequestBuilderProvider: NetworkRequestBuilderProvider,
    private val connectivityManager: ConnectivityManager,
    private val wifiManager: WifiManager,
) {

    val wifiState: Flow<Wifi?> = callbackFlow<Network?> {
        var registeredCallback: ConnectivityManager.NetworkCallback? = null

        fun callbackRefresh(network: Network?) {
            appScope.launch {
                send(network)
            }
        }

        try {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    log(TAG, VERBOSE) { "onAvailable(network=$network)" }
                    callbackRefresh(network)
                }

                /**
                 * Some devices don't update the active network fast enough.
                 * 200ms gave good results on a Pixel 2.
                 */
                override fun onLost(network: Network) {
                    log(TAG, VERBOSE) { "onLost(network=$network)" }
                    callbackRefresh(null)
                }

                /**
                 * Not consistently called in all Android versions
                 * https://issuetracker.google.com/issues/144891976
                 */
                override fun onUnavailable() {
                    log(TAG, VERBOSE) { "onUnavailable()" }
                    callbackRefresh(null)
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    log(TAG, VERBOSE) { "onLosing(network=$network, maxMsToLive=$maxMsToLive)" }
                    callbackRefresh(network)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    log(TAG, VERBOSE) { "onCapabilitiesChanged(network=$network, capabilities=$networkCapabilities)" }
                    callbackRefresh(network)
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    log(TAG, VERBOSE) { "onLinkPropertiesChanged(network=$network, linkProperties=$linkProperties)" }
                    callbackRefresh(network)
                }

                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                    log(TAG, VERBOSE) { "onBlockedStatusChanged(network=$network, blocked=$blocked)" }
                    callbackRefresh(network)
                }
            }

            val request = networkRequestBuilderProvider.get()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            /**
             * This may throw java.lang.SecurityException on Samsung devices
             * java.lang.SecurityException:
             *  at android.os.Parcel.createExceptionOrNull (Parcel.java:2385)
             *  at android.net.ConnectivityManager.registerNetworkCallback (ConnectivityManager.java:4564)
             */
            connectivityManager.registerNetworkCallback(request, callback)
            registeredCallback = callback
        } catch (e: SecurityException) {
            log(TAG, ERROR) {
                "registerNetworkCallback() threw an undocumented SecurityException, Just Samsung Things™️:${e.asLog()}"
            }
        }

        awaitClose {
            log(TAG, VERBOSE) { "unregisterNetworkCallback($registeredCallback)" }
            registeredCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        }
    }
        .map { network ->
            if (network == null) return@map null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@map null
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return@map null

            val ssid = if (hasApiLevel(29) && context.hasPermission(Permission.ACCESS_FINE_LOCATION)) {
                wifiManager.connectionInfo.ssid
            } else if (hasApiLevel(27) && context.hasPermission(Permission.ACCESS_COARSE_LOCATION)) {
                wifiManager.connectionInfo.ssid
            } else {
                wifiManager.connectionInfo.ssid
            }

            Wifi(
                signalStrength = if (hasApiLevel(30)) {
                    wifiManager.calculateSignalLevel(capabilities.signalStrength) / wifiManager.maxSignalLevel.toFloat()
                } else {
                    WifiManager.calculateSignalLevel(capabilities.signalStrength, 6) / 6f
                }.takeIf { it.isFinite() } ?: -1f,
                ssid = ssid,
                addressIpv4 = linkProperties.linkAddresses.firstOrNull { it.address is Inet4Address }?.address as Inet4Address?,
                addressIpv6 = linkProperties.linkAddresses.firstOrNull { it.address is Inet6Address }?.address as Inet6Address?,
                frequency = wifiManager.connectionInfo?.frequency,
            )
        }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "wifiState" }
        .replayingShare(scope = appScope)

    data class Wifi(
        val signalStrength: Float,
        val ssid: String?,
        val addressIpv4: Inet4Address?,
        val addressIpv6: Inet6Address?,
        val frequency: Int?,
    )

    companion object {
        private val TAG = logTag("WifiStateProvider")
    }
}
