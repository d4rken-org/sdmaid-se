package eu.darken.sdmse.corpsefinder.core.watcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.hasCause
import eu.darken.sdmse.common.pkgs.isSelfComponentExplicitlyEnabled
import eu.darken.sdmse.common.pkgs.toggleSelfComponent
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the [UninstallWatcherReceiver] component's enabled state in sync with `isPro && isWatcherEnabled`.
 *
 * The flag lives in our DataStore while the enabled state lives in the system's package restrictions,
 * and routes exist that move one without the other (Auto Backup / device transfer, the in-app config
 * restore, "Clear data"). Reconciling at app start and on every later change repairs such a divergence
 * no matter which route produced it.
 */
@Singleton
class UninstallWatcherControl @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val packageManager: PackageManager,
    private val settings: CorpseFinderSettings,
    private val upgradeRepo: UpgradeRepo,
) {

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        log(TAG, VERBOSE) { "start()" }

        combine(
            settings.isWatcherEnabled.flow,
            upgradeRepo.upgradeInfo,
        ) { enabled, info ->
            when {
                !enabled -> false
                info.isSettled && info.error == null -> info.isPro
                // An entitlement that hasn't resolved yet, or whose lookup failed, is not an answer of
                // "not pro" - acting on it would disable a paying user's receiver. Leave it untouched.
                else -> null
            }
        }
            .filterNotNull()
            .catch {
                // DataStoreValue.flow carries no catch and @AppScope has no CoroutineExceptionHandler,
                // so an escaping throw here would kill the process at app start. No retry: a corrupt
                // preferences file stays corrupt, the next launch tries again.
                if (it.hasCause(CancellationException::class)) throw it
                log(TAG, WARN) { "Reconciliation flow failed: ${it.asLog()}" }
            }
            .onEach { desired -> reconcile(desired) }
            .launchIn(appScope)
    }

    private fun reconcile(desired: Boolean) {
        try {
            val component = ComponentName(context, UninstallWatcherReceiver::class.java)
            // The receiver ships `enabled="false"`, so DEFAULT and DISABLED are the same outcome here.
            val current = packageManager.isSelfComponentExplicitlyEnabled(component)
            log(TAG, INFO) { "reconcile(): desired=$desired, current=$current, write=${desired != current}" }
            if (desired == current) return
            packageManager.toggleSelfComponent(component, desired)
        } catch (e: Exception) {
            log(TAG, WARN) { "reconcile($desired) failed: ${e.asLog()}" }
        }
    }

    companion object {
        private val TAG = logTag("CorpseFinder", "Watcher", "Uninstall", "Control")
    }
}
