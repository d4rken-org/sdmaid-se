package eu.darken.sdmse.common.root

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.GeneralSettings
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.rxshell.root.RootContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val dispatcherProvider: DispatcherProvider,
) {

    private var cachedContext: RootContext? = null
    private val cacheLock = Mutex()

    suspend fun isRooted(): Boolean = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "isRooted()?" }

        when {
            generalSettings.isRootDisabled -> {
                log(TAG, WARN) { "Rootcheck is disabled!" }
                false
            }
            else -> cacheLock.withLock {
                cachedContext?.let { return@withContext it.isRooted }

                try {
                    RootContext.Builder(context).build()
                        .timeout(15, TimeUnit.SECONDS)
                        .blockingGet()
                        .also {
                            cachedContext = it
                            log(TAG) { "New RootContext obtained: $it" }
                        }
                        .isRooted
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Error while obtaining RootContext: ${e.asLog()}" }
                    false
                }
            }
        }
    }

    companion object {
        internal val TAG = logTag("Root", "Manager")
    }
}
