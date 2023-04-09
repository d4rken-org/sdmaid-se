package eu.darken.sdmse.common.root.service.internal

import android.annotation.SuppressLint
import android.content.Intent
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.lang.reflect.Method
import javax.inject.Inject

class ReflectionBroadcast @Inject constructor() {

    /**
     * Retrieve value of Intent.FLAG_RECEIVER_FROM_SHELL, if it exists<br></br>
     * <br></br>
     * Stability: stable, even if the flag goes away again this is unlikely to affect things
     *
     * @return FLAG_RECEIVER_FROM_SHELL or 0
     */

    private val flagReceiverFromShell: Int by lazy {
        try {
            @SuppressLint("SoonBlockedPrivateApi")
            val fFlagReceiverFromShell = Intent::class.java.getDeclaredField("FLAG_RECEIVER_FROM_SHELL")
            fFlagReceiverFromShell.getInt(null)
        } catch (e: NoSuchFieldException) {
            // not present on all Android versions
            0
        } catch (e: IllegalAccessException) {
            log(TAG, ERROR) { "Failed to determine 'flagReceiverFromShell': ${e.asLog()}" }
            0
        }
    }

    /**
     * Retrieve ActivityManager instance without needing a context<br></br>
     * <br></br>
     * Stability: has changed before, might change again, rare
     *
     * @return ActivityManager
     */
    private val activityManager: Any by lazy {
        // Return object is AIDL interface IActivityManager, not an ActivityManager or ActivityManagerService

        try { // marked deprecated in Android source
            @SuppressLint("PrivateApi")
            val cActivityManagerNative = Class.forName("android.app.ActivityManagerNative")
            val mGetDefault = cActivityManagerNative.getMethod("getDefault")
            return@lazy mGetDefault.invoke(null)
        } catch (e: Exception) {
            log(TAG, WARN) { "activityManager via ActivityManagerNative failed: ${e.asLog()}" }
        }
        try { // alternative
            val cActivityManager = Class.forName("android.app.ActivityManager")
            val mGetService = cActivityManager.getMethod("getService")
            return@lazy mGetService.invoke(null)
        } catch (e: Exception) {
            log(TAG, ERROR) { "activityManager via ActivityManager failed: ${e.asLog()}" }
        }
        throw RuntimeException("Unable to retrieve ActivityManager")
    }

    /**
     * ActivityManager.broadcastIntent() method
     */
    private val broadcastIntent: Method by lazy<Method> {
        log { "broadcastIntent - init" }
        for (m in activityManager.javaClass.methods) {
            if (m.name == "broadcastIntent" && m.parameterTypes.size == 13) {
                log { "broadcastIntent - size=13" }
                // API 24+
                return@lazy m
            }
            if (m.name == "broadcastIntent" && m.parameterTypes.size == 12) {
                log { "broadcastIntent - size=12" }
                // API 21+
                return@lazy m
            }
        }
        throw RuntimeException("Unable to retrieve broadcastIntent method")
    }

    /**
     * Broadcast intent<br></br>
     * <br></br>
     * Stability: the implementation for this will definitely change over time<br></br>
     * <br></br>
     * This implementation does not require us to have a context
     *
     * @param intent Intent to broadcast
     */
    @SuppressLint("PrivateApi")
    fun sendBroadcast(intent: Intent, userId: Int) {
        try {
            log { "sendBroadcast(userId=$userId, intent=${intent})..." }
            // Prevent system from complaining about unprotected broadcast, if the field exists
            intent.flags = flagReceiverFromShell
            log { "sendBroadcast(...) flags prepared" }

            // API 24+
            // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java;l=14427
            if (broadcastIntent.parameterTypes.size == 13) {
                log { "sendBroadcast(...) sending (type=13)" }

                broadcastIntent.invoke(
                    activityManager,
                    null, // IApplicationThread caller
                    intent, // Intent intent
                    null, // String resolvedType
                    null, // IIntentReceiver resultTo
                    0, // int resultCode
                    null, // String resultData
                    null, // Bundle resultExtras
                    null, // String[] requiredPermissions
                    -1, // int appOp,
                    null, // Bundle bOptions
                    false, // boolean serialized
                    false, // boolean sticky
                    userId, // int userId
                )
                log { "sendBroadcast(..) (type=13) done." }
                return
            }

            if (broadcastIntent.parameterTypes.size == 12) {
                log { "sendBroadcast(...) sending (type=12)" }
                // API 21+
                broadcastIntent.invoke(
                    activityManager,
                    null,
                    intent,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    -1,
                    false,
                    false,
                    userId,
                )
                log { "sendBroadcast(..) (type=12) done." }
                return
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "sendBroadcast(...) failed: ${e.asLog()}" }
            throw RuntimeException("Unable to send broadcast", e)
        }
    }

    companion object {
        private val TAG = logTag("Root", "ReflectionBroadcast")
    }
}