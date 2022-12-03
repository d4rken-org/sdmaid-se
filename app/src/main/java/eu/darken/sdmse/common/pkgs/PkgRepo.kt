package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PkgRepo @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val pkgOps: PkgOps,
    private val pkgEventListener: PackageEventListener,
) {

    private val cacheLock = Mutex()
    private val pkgCache = mutableMapOf<Pkg.Id, CachedInfo>()

    init {
        pkgEventListener.events
            .onEach {
                cacheLock.withLock {
                    pkgCache.clear()
                }
            }
            .launchIn(appScope)
    }

    private suspend fun queryCache(pkgId: Pkg.Id): CachedInfo = cacheLock.withLock {
        pkgCache[pkgId]?.let { return@withLock it }

        log(TAG, VERBOSE) { "Cache miss for $pkgId" }
        val queried = pkgOps.queryPkg(pkgId)

        return CachedInfo(
            id = pkgId,
            installed = queried,
        ).also {
            pkgCache[pkgId] = it
        }
    }

    suspend fun isInstalled(pkgId: Pkg.Id): Boolean = queryCache(pkgId).isInstalled

    data class CachedInfo(
        val id: Pkg.Id,
        val installed: Installed?,
    ) {
        val isInstalled: Boolean
            get() = installed != null
    }

    companion object {
        private val TAG = logTag("PkgRepo")
    }
}