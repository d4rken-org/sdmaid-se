package eu.darken.sdmse.common.pkgs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class PackageEventListener @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:AppScope private val appScope: CoroutineScope,
) {
    sealed class Event {
        data class PackageInstalled(val packageId: Pkg.Id) : Event()
        data class PackageRemoved(val packageId: Pkg.Id) : Event()
    }

    val events: Flow<Event> = callbackFlow {
        fun sendAsync(intent: Intent) {
            launch {
                try {
                    val event = when (intent.action) {
                        Intent.ACTION_PACKAGE_ADDED -> {
                            val pkgId = intent.data?.encodedSchemeSpecificPart?.toPkgId()
                                ?: throw IllegalArgumentException("Package Info is missing in ${intent.data}")
                            Event.PackageInstalled(pkgId)
                        }

                        Intent.ACTION_PACKAGE_REMOVED -> {
                            val pkgId = intent.data?.encodedSchemeSpecificPart?.toPkgId()
                                ?: throw IllegalArgumentException("Package Info is missing in ${intent.data}")
                            Event.PackageRemoved(pkgId)
                        }

                        else -> throw IllegalArgumentException("Unknown intent action: ${intent.action}")
                    }
                    send(event)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Sending event failed: ${e.asLog()}" }
                }
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log(TAG) { "onReceive(context=$context, intent=$intent)" }
                sendAsync(intent)
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        ContextCompat.registerReceiver(context, receiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        awaitClose {
            log(TAG) { "unregisterReceiver($receiver)" }
            context.unregisterReceiver(receiver)
        }
    }
        .shareIn(appScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 3000), 0)
        .setupCommonEventHandlers(TAG) { "events" }


    companion object {
        private val TAG = logTag("Pkg", "EventListener")
    }
}