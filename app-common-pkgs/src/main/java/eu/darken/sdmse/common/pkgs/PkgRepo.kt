package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.collections.mutate
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.sources.PackageManagerPkgSource
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class PkgRepo @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    pkgEventListener: PackageEventListener,
    private val pkgSources: Set<@JvmSuppressWildcards PkgDataSource>,
    private val gatewaySwitch: GatewaySwitch,
    private val pkgOps: PkgOps,
    private val userManager: UserManager2,
) {

    data class CacheContainer(
        val isInitialized: Boolean = false,
        val pkgData: Map<CacheKey, CachedInfo> = emptyMap(),
    )

    private val cacheLock = Mutex()
    private val pkgCache = MutableStateFlow(CacheContainer())

    val pkgs: Flow<Collection<Installed>> = pkgCache
        .filter { it.isInitialized }
        .map { it.pkgData }
        .map { cachedInfo -> cachedInfo.values.mapNotNull { it.data } }
        .onStart {
            cacheLock.withLock {
                if (!pkgCache.value.isInitialized) {
                    log(TAG) { "Init due to pkgs subscription" }
                    load()
                }
            }
        }
        .replayingShare(appScope)

    init {
        pkgEventListener.events
            .onEach {
                log(TAG) { "Refreshing package cache due to event: $it" }
                Bugs.leaveBreadCrumb("Installed package data has changed")
                refresh()
            }
            .launchIn(appScope)
    }

    private suspend fun load() {
        log(TAG) { "load()" }
        pkgCache.value = CacheContainer(
            isInitialized = true,
            pkgData = generatePkgcache()
        )
    }

    private suspend fun generatePkgcache(): Map<CacheKey, CachedInfo> {
        log(TAG) { "Generating package cache" }
        return gatewaySwitch.useRes {
            pkgOps.useRes {
                val sourceMap: Map<KClass<out PkgDataSource>, Collection<Installed>> = pkgSources.associate { source ->
                    val fromSource = source.getPkgs()
                    log(TAG) { "${fromSource.size} pkgs from $source" }
                    source::class to fromSource
                }

                val mergedData = mutableMapOf<CacheKey, CachedInfo>()

                // This is our primary source of data, we don't overwrite this data with data from other sources
                sourceMap[PackageManagerPkgSource::class]!!.forEach { pkg ->
                    val key = CacheKey(pkg)
                    mergedData[key] = CachedInfo(key, pkg)
                }

                sourceMap
                    .filter { it.key != PackageManagerPkgSource::class }
                    .onEach { (type, pkgs) ->
                        val extraPkgs = pkgs
                            .map { pkg ->
                                val key = CacheKey(pkg)
                                key to CachedInfo(key, pkg)
                            }
                            .filter { (key, _) -> !mergedData.containsKey(key) }
                            .associate { (key, info) -> key to info }

                        if (Bugs.isTrace) {
                            log(TAG) { "${extraPkgs.size} extra pkgs from $type" }
                            extraPkgs.forEach { log(TAG, VERBOSE) { "Extra pkg from $type: ${it.value}" } }
                        }

                        mergedData.putAll(extraPkgs)
                    }

                log(TAG) { "Pkgs total: ${mergedData.size}" }
                mergedData.values.forEach { log(TAG, VERBOSE) { "Installed package: $it" } }

                mergedData
            }
        }
    }

    private suspend fun queryCache(
        pkgId: Pkg.Id,
        userHandle: UserHandle2?,
    ): Set<CachedInfo> = cacheLock.withLock {
        if (!pkgCache.value.isInitialized) {
            log(TAG) { "Package cache doesn't exist yet..." }
            load()
        }
        val systemHandle = userManager.systemUser().handle

        val infos = pkgCache.value.pkgData.values.filter {
            it.key.pkgId == pkgId && (userHandle == null || userHandle == systemHandle || it.key.userHandle == userHandle)
        }
        if (infos.isNotEmpty()) return@withLock infos.toSet()

        log(TAG, VERBOSE) { "Cache miss for $pkgId:$userHandle" }

        // We didn't have any cache matches
        if (userHandle != null) {
            val key = CacheKey(pkgId, userHandle)
            val cacheInfo = CachedInfo(key, null)

            pkgCache.value = pkgCache.value.copy(
                pkgData = pkgCache.value.pkgData.mutate {
                    this[key] = cacheInfo
                }
            )

            setOf(cacheInfo)
        } else {
            emptySet()
        }
    }

    suspend fun query(
        pkgId: Pkg.Id,
        userHandle: UserHandle2?,
    ): Collection<Installed> = queryCache(pkgId, userHandle).mapNotNull { it.data }

    suspend fun refresh(
        id: Pkg.Id,
        userHandle: UserHandle2? = null
    ): Collection<Installed> {
        log(TAG) { "refresh(): $id" }
        // TODO refreshing the whole cache is inefficient, implement single target refresh?
        cacheLock.withLock { load() }
        return queryCache(id, userHandle).mapNotNull { it.data }
    }

    suspend fun refresh(): Collection<Installed> = cacheLock.withLock {
        log(TAG) { "refresh()" }
        load()
        pkgCache.value.pkgData.mapNotNull { it.value.data }
    }

    data class CacheKey(
        val pkgId: Pkg.Id,
        val userHandle: UserHandle2,
    ) {
        constructor(pkg: Installed) : this(pkg.id, pkg.userHandle)
    }

    data class CachedInfo(
        val key: CacheKey,
        val data: Installed?,
    ) {
        val isInstalled: Boolean
            get() = data != null
    }

    companion object {
        private val TAG = logTag("PkgRepo")
    }
}