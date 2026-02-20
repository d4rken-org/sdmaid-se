package eu.darken.sdmse.automation.core

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@Reusable
class ScreenState @Inject constructor(
    @ApplicationContext private val context: Context,
    private val powerManager: PowerManager,
    private val keyguardManager: KeyguardManager,
) {

    private suspend fun isScreenOn(): Boolean = powerManager.isInteractive

    private suspend fun isUnlocked(): Boolean = !keyguardManager.isKeyguardLocked

    val state: Flow<State> = callbackFlow {
        trySendBlocking(State(isScreenOn = isScreenOn(), isUnlocked = isUnlocked()))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log(TAG) { "onReceive(context=$context, intent=$intent)" }

                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        runBlocking {
                            trySend(State(isScreenOn = true, isUnlocked = isUnlocked()))
                        }
                    }

                    Intent.ACTION_SCREEN_OFF -> {
                        runBlocking {
                            trySend(State(isScreenOn = false, isUnlocked = false))
                        }
                    }

                    Intent.ACTION_USER_PRESENT -> {
                        runBlocking {
                            trySend(State(isScreenOn = isScreenOn(), isUnlocked = true))
                        }
                    }

                    else -> log(ERROR) { "Unknown intent: $intent" }
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        // ACTION_USER_PRESENT is not being send without RECEIVER_EXPORTED, seems like a bug?
        ContextCompat.registerReceiver(context, receiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)

        awaitClose {
            log(TAG, VERBOSE) { "unregisterReceiver($receiver)" }
            context.unregisterReceiver(receiver)
        }
    }
        .setupCommonEventHandlers(TAG) { "state" }

    data class State(
        val isScreenOn: Boolean,
        val isUnlocked: Boolean,
    ) {
        val isScreenAvailable: Boolean
            get() = isScreenOn && isUnlocked
    }

    companion object {
        private val TAG = logTag("Automation", "ScreenState")
    }
}