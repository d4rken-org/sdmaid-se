package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.collections.mutate
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
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
    private val pkgCache = MutableStateFlow(mapOf<Pkg.Id, CachedInfo>())
    val pkgs: Flow<Collection<Installed>> = pkgCache.map { cachedInfo ->
        cachedInfo.values.mapNotNull { it.data }
    }

    init {
        pkgEventListener.events
            .onEach {
                cacheLock.withLock {
                    pkgCache.value = pkgOps.getInstalledPackages()
                        .map { CachedInfo(id = it.id, data = it) }
                        .associateBy { it.id }
                }
            }
            .launchIn(appScope)
    }

    private suspend fun queryCache(pkgId: Pkg.Id): CachedInfo = cacheLock.withLock {
        pkgCache.value[pkgId]?.let { return@withLock it }

        log(TAG, VERBOSE) { "Cache miss for $pkgId" }
        val queried = pkgOps.queryPkg(pkgId)

        return CachedInfo(
            id = pkgId,
            data = queried,
        ).also {
            pkgCache.value = pkgCache.value.mutate {
                this[pkgId] = it
            }
        }
    }

    suspend fun isInstalled(pkgId: Pkg.Id): Boolean = queryCache(pkgId).isInstalled

    suspend fun getPkg(pkgId: Pkg.Id): Installed? = queryCache(pkgId).data

    data class CachedInfo(
        val id: Pkg.Id,
        val data: Installed?,
    ) {
        val isInstalled: Boolean
            get() = data != null
    }

    companion object {
        private val TAG = logTag("PkgRepo")
    }
}