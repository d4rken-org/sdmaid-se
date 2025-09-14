package eu.darken.sdmse.common.locale

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.flow.shareLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocaleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
) {

    private suspend fun getCurrentLocales() = context.resources.configuration.locales

    val currentLocales: Flow<LocaleList> = callbackFlow {
        fun updateLocales() = launch {
            val locales = getCurrentLocales()
            log(TAG) { "updateLocales(): $locales" }
            send(locales)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log(TAG) { "onReceive(context=$context, intent=$intent)" }

                when (intent.action) {
                    Intent.ACTION_LOCALE_CHANGED -> updateLocales()

                    else -> log(ERROR) { "Unknown intent: $intent" }
                }
            }

        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_LOCALE_CHANGED),
            RECEIVER_NOT_EXPORTED
        )

        updateLocales()

        awaitClose {
            log { "unregisterReceiver($receiver)" }
            context.unregisterReceiver(receiver)
        }
    }
        .setupCommonEventHandlers(TAG) { "currentLocales" }
        .shareLatest(appScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 3000))

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun showLanguagePicker() {
        fun startPicker(intent: Intent) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        try {
            startPicker(defaultIntent)
            return
        } catch (e: ActivityNotFoundException) {
            log(TAG, ERROR) { "Failed to open ACTION_APP_LOCALE_SETTINGS: ${e.asLog()}" }
        }
        try {
            startPicker(directIntent)
            return
        } catch (e: ActivityNotFoundException) {
            log(TAG, ERROR) { "Failed to open AppLocalePickerActivity: ${e.asLog()}" }
        }

        // This always works, well it should, otherwise let the caller handle the exception
        startPicker(fallbackIntent)
    }

    @get:RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val defaultIntent: Intent
        get() = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    @get:RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val directIntent: Intent
        get() = Intent().apply {
            component = ComponentName(
                "com.android.settings",
                "com.android.settings.localepicker.AppLocalePickerActivity",
            )
            data = Uri.fromParts("package", context.packageName, null)
        }

    private val fallbackIntent: Intent
        get() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    companion object {
        private val TAG = logTag("Locale", "Manager")
    }
}