package eu.darken.sdmse.common.pkgs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class PackageEventListener @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
) {
    sealed class Event {
        data class PackageInstalled(val packageId: Pkg.Id) : Event()
        data class PackageRemoved(val packageId: Pkg.Id) : Event()
    }

    val events: Flow<Event> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log { "onReceive(context=$context, intent=$intent)" }

                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED -> {
                        val pkgId = intent.data?.encodedSchemeSpecificPart?.toPkgId()
                        pkgId?.let { trySendBlocking(Event.PackageInstalled(it)) }
                    }
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        val pkgId = intent.data?.encodedSchemeSpecificPart?.toPkgId()
                        pkgId?.let { trySendBlocking(Event.PackageRemoved(it)) }
                    }
                    else -> log(ERROR) { "Unknown intent: $intent" }
                }
            }

        }

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        context.registerReceiver(receiver, intentFilter)

        awaitClose {
            log { "unregisterReceiver($receiver)" }
            context.unregisterReceiver(receiver)
        }
    }
        .shareIn(appScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 3000), 0)
        .setupCommonEventHandlers("AppEventListener") { "events" }

}