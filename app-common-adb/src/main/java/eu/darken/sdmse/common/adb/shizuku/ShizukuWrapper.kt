package eu.darken.sdmse.common.adb.shizuku

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val gateway: PorterGateway,
) {

    /**
     * Packages that declare a manager permission of EITHER family, in [ALL_MANAGER_PERMISSIONS] order.
     *
     * Detects managers via their permissions instead of a fixed package name. The permission names
     * are shared across Shizuku forks, so this keeps working when a fork hides its package from
     * enumeration ("Hide Shizuku from other apps") or ships under a different package name.
     * Permissions live in a global namespace, so the lookup isn't subject to the package-visibility
     * filtering that hides the app itself. Every name is tried because Shizuku+'s Plus flavor
     * declares only its own permission and just requests the stock one.
     *
     * Answers "is this app an ADB manager", so it deliberately spans both families.
     */
    suspend fun getManagerPackages(): List<String> = withContext(dispatcherProvider.IO) {
        ALL_MANAGER_PERMISSIONS.mapNotNull { resolvePermissionOwner(it) }.distinct()
    }

    /**
     * Packages declaring a manager permission of [backend]'s family, the [activeBackend] if null.
     *
     * Everything we connect to goes through the active backend, so the other family's manager must
     * never be offered as a fallback: opening it can't affect the link we are waiting on.
     */
    suspend fun getActiveManagerPackages(backend: AdbBackend? = null): List<String> {
        val permissions = when (backend ?: activeBackend()) {
            AdbBackend.PORTER -> PORTER_PERMISSIONS
            AdbBackend.SHIZUKU -> MANAGER_PERMISSIONS
        }
        return withContext(dispatcherProvider.IO) {
            permissions.mapNotNull { resolvePermissionOwner(it) }.distinct()
        }
    }

    /** The manager package to treat as *the* ADB manager app, see [getActiveManagerPackages]. */
    suspend fun getActiveManagerPackage(backend: AdbBackend? = null): String? =
        getActiveManagerPackages(backend).firstOrNull()

    private fun resolvePermissionOwner(permission: String): String? = try {
        context.packageManager
            .getPermissionInfo(permission, 0)
            .packageName
            ?.takeUnless { it.isBlank() }
    } catch (e: PackageManager.NameNotFoundException) {
        log(TAG) { "resolvePermissionOwner($permission): not declared by any app" }
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "resolvePermissionOwner($permission): Lookup failed: ${e.asLog()}" }
        null
    }

    val link: Flow<AdbLink?> = gateway.link

    /**
     * Emits when the current link's permission state changes. The state a link attached with is not
     * re-announced, [link] itself emits for that.
     */
    val permissionChanges: Flow<Unit> = gateway.link
        .flatMapLatest { link ->
            link?.permission
                ?.drop(1)
                ?.catch { log(TAG, WARN) { "permissionChanges: $link failed: ${it.asLog()}" } }
                ?: emptyFlow()
        }
        .map { granted ->
            log(TAG) { "permissionChanges: granted=$granted" }
            Unit
        }
        .catch { log(TAG, WARN) { "permissionChanges failed: ${it.asLog()}" } }

    /** Overridden in tests to keep the wedge cases fast, never in production. */
    internal var ipcTimeoutMs: Long = IPC_TIMEOUT_MS

    private suspend fun currentLink(): AdbLink? = gateway.link.first()

    /** Null means "cannot know": no link, no answer in time, or the call failed. */
    suspend fun permission(): AdbPermission? {
        val link = currentLink()
        if (link == null) {
            log(TAG) { "permission(): No link" }
            return null
        }
        return try {
            withTimeoutOrNull(ipcTimeoutMs) { link.checkPermission() }
                .also { if (it == null) log(TAG, WARN) { "permission(): No answer within ${ipcTimeoutMs}ms" } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "permission(): ${e.asLog()}" }
            null
        }.also { log(TAG) { "permission()=$it" } }
    }

    /** Null means "cannot know", see [permission]. */
    suspend fun isGranted(): Boolean? = permission()?.isGranted

    /**
     * Shows the manager's permission prompt and suspends until the user answered. Null when there is
     * no link or the request failed, e.g. because the link was lost meanwhile.
     *
     * Deliberately unbounded: the user may take a while to read the prompt. Callers bound the wait.
     */
    suspend fun requestPermission(): AdbPermission? {
        val link = currentLink()
        if (link == null) {
            log(TAG, WARN) { "requestPermission(): No link" }
            return null
        }
        log(TAG) { "requestPermission() on $link" }
        return try {
            link.requestPermission()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "requestPermission() failed: ${e.asLog()}" }
            null
        }.also { log(TAG) { "requestPermission()=$it" } }
    }

    /** Diagnostics only: the UID the privileged helper runs as (2000 for shell), null without a link. */
    suspend fun serverUid(): Int? = currentLink()?.uid.also { log(TAG) { "serverUid()=$it" } }

    /** Null means unknown: no answer within [ipcTimeoutMs] or the lookup failed. */
    suspend fun availability(): AdbAvailability? = try {
        withTimeoutOrNull(ipcTimeoutMs) { gateway.availability() }
            .also { if (it == null) log(TAG, WARN) { "availability(): No answer within ${ipcTimeoutMs}ms" } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "availability() failed: ${e.asLog()}" }
        null
    }.also { log(TAG) { "availability()=$it" } }

    /**
     * The backend an [availability] snapshot points at. An installed Porter always wins, so an
     * unknown availability falls back to whether any package declares Porter's permission.
     */
    suspend fun backendOf(availability: AdbAvailability?): AdbBackend = when (availability) {
        is AdbAvailability.Installed -> availability.backend
        is AdbAvailability.Incompatible -> availability.backend
        AdbAvailability.NotInstalled -> AdbBackend.SHIZUKU
        null -> withContext(dispatcherProvider.IO) {
            if (PORTER_PERMISSIONS.any { resolvePermissionOwner(it) != null }) AdbBackend.PORTER else AdbBackend.SHIZUKU
        }
    }

    /** Not latched: the SDK resolves the backend again whenever it holds no connection. */
    suspend fun activeBackend(): AdbBackend = backendOf(availability()).also { log(TAG) { "activeBackend()=$it" } }

    companion object {
        private val TAG = logTag("ADB", "Shizuku", "Wrapper")

        internal const val PORTER_PERMISSION = "eu.darken.porter.permission.API"
        internal const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
        internal const val SHIZUKU_PLUS_PERMISSION = "af.shizuku.plus.permission.API_V23"

        // Priority order: the stock permission first, so an install that defines both (Shizuku+ Drop-In,
        // or Shizuku+ Plus next to its Compat Hub) keeps resolving to the same package it does today.
        internal val MANAGER_PERMISSIONS: List<String> = listOf(SHIZUKU_PERMISSION, SHIZUKU_PLUS_PERMISSION)

        // Porter's own family, deliberately not folded into MANAGER_PERMISSIONS: the Porter manager
        // removes the stock Shizuku permission instead of declaring it, so a Shizuku-family lookup
        // can never resolve to it, and the ACTIVE-manager lookup must not mix the two.
        internal val PORTER_PERMISSIONS: List<String> = listOf(PORTER_PERMISSION)

        internal val ALL_MANAGER_PERMISSIONS: List<String> = PORTER_PERMISSIONS + MANAGER_PERMISSIONS

        /**
         * Budget for a single call to the manager's server.
         *
         * Deliberately generous rather than tight: the job here is only to turn "never returns" into
         * "eventually gives up". A too-tight bound would report a slow-but-working server as
         * unavailable, and low-end devices under memory pressure are where both a real wedge and a
         * slow answer are most likely.
         */
        internal const val IPC_TIMEOUT_MS = 15 * 1000L
    }
}
