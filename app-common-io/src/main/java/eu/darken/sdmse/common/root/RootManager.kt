package eu.darken.sdmse.common.root

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.root.service.RootServiceClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    val serviceClient: RootServiceClient,
    private val settings: RootSettings,
) {

    private val refreshTrigger = MutableStateFlow(0)

    /** A probe answer is only valid for the setting value and generation it was taken under. */
    private data class ProbeKey(val useRoot: Boolean?, val generation: Int)

    private var cached: Pair<ProbeKey, Boolean>? = null
    private val cacheLock = Mutex()

    private val probeInput: Flow<ProbeKey> =
        combine(settings.useRoot.flow, refreshTrigger) { setting, gen -> ProbeKey(setting, gen) }

    private suspend fun isRootedFor(key: ProbeKey): Boolean = withContext(dispatcherProvider.IO) {
        cacheLock.withLock {
            cached?.takeIf { it.first == key }?.let { return@withContext it.second }

            val newState = try {
                serviceClient.get().use { it.item.ipc.checkBase() != null }
            } catch (e: CancellationException) {
                throw e // don't cache a cancelled probe as "not rooted"
            } catch (e: Exception) {
                log(TAG, WARN) { "Error while checking for root: $e" }
                false
            }
            log(TAG, INFO) { "isRooted=$newState" }
            newState.also { cached = key to it }
        }
    }

    /**
     * Is the device rooted and we have access?
     */
    suspend fun isRooted(): Boolean = isRootedFor(ProbeKey(settings.useRoot.value(), refreshTrigger.value))

    /** Invalidate the memoised probe and make the derived state flows re-evaluate. */
    fun refresh() {
        log(TAG) { "refresh()" }
        // Not suspending and deliberately not taking cacheLock: an in-flight probe holds that lock
        // across the su bind, and a retry must neither block behind it nor discard its result. The
        // running probe stores under the old key and is simply never read again.
        // update {} and not `value += 1`: a SetupManager.refresh() racing a Retry tap can otherwise
        // have both callers read the same generation and write the same successor, dropping one
        // refresh — the retry would then reuse the cached answer and silently do nothing.
        refreshTrigger.update { it + 1 }
    }

    /** Exists so tests can assert that concurrent refreshes do not lose increments. */
    internal val currentGeneration: Int get() = refreshTrigger.value

    /**
     * Did the user consent to SD Maid using root and is root available?
     */
    // StateFlow, so equal values are conflated: a retry that changes nothing does not ripple
    // downstream. [accessState] does re-emit, so the setup card can show the probe running.
    val useRoot: Flow<Boolean> = probeInput
        .mapLatest { key -> (key.useRoot ?: false) && isRootedFor(key) }
        .setupCommonEventHandlers(TAG) { "useRoot" }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 10 * 1000,
                replayExpirationMillis = 0,
            ),
            initialValue = null,
        )
        .filterNotNull()

    /**
     * Probe-aware status for UI gating: distinguishes "not decided" / "checking" / "active" /
     * "opted in but unavailable" / "opted out". Shares [isRooted]'s cache, so it does not trigger
     * an additional su bind. [AccessState.Active] is equivalent to [useRoot] being true.
     */
    val accessState: Flow<AccessState> = probeInput
        .flatMapLatest { key ->
            when (key.useRoot) {
                null -> flowOf(AccessState.Undecided)
                false -> flowOf(AccessState.Declined)
                true -> flow {
                    emit(AccessState.Checking)
                    emit(if (isRootedFor(key)) AccessState.Active else AccessState.Unavailable)
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "accessState" }
        .replayingShare(appScope)

    suspend fun isInstalled(): Boolean {
        val installed = KNOWN_ROOT_MANAGERS.any {
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(it, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        log(TAG) { "isInstalled(): $installed" }
        return installed
    }

    companion object {
        internal val TAG = logTag("Root", "Manager")
        private val KNOWN_ROOT_MANAGERS = setOf(
            "com.topjohnwu.magisk",
            "me.weishu.kernelsu",
            "com.rifsxd.ksunext",
            "me.bmax.apatch",
            "com.sukisu.ultra",
        )
    }
}
