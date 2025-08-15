package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.collections.mutate
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.sources.NormalPkgsSource
import eu.darken.sdmse.common.sharedresource.closeAll
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class PkgRepo @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    pkgEventListener: PackageEventListener,
    private val pkgSources: Set<@JvmSuppressWildcards PkgDataSource>,
    private val gatewaySwitch: GatewaySwitch,
    private val pkgOps: PkgOps,
    private val shellOps: ShellOps,
    private val userManager: UserManager2,
) {

    private val cache = DynamicStateFlow(if (Bugs.isTrace) TAG else null, appScope + dispatcherProvider.IO) {
        log(TAG, INFO) { "Initializing pkg cache" }
        generateCacheContainer()
    }

    data class PkgData(
        internal val pkgMap: Map<CacheKey, CachedInfo> = emptyMap(),
        val error: Exception? = null,
    ) {

        val pkgs: Collection<Installed>
            get() {
                if (error != null) throw error
                return pkgMap.values.mapNotNull { it.data }
            }

        internal val pkgCount: Int
            get() = pkgMap.count { it.value.data != null }

        companion object {
            fun from(pkgs: Collection<Installed>) = PkgData(
                pkgMap = pkgs.associate {
                    val key = CacheKey(it.id, it.userHandle)
                    key to CachedInfo(key, it)
                }
            )
        }
    }

    val data: Flow<PkgData> = cache.flow
        .replayingShare(appScope)

    init {
        pkgEventListener.events
            .onEach {
                log(TAG, INFO) { "Refreshing package cache due to event: $it" }
                Bugs.leaveBreadCrumb("Installed package data has changed")
                refresh()
            }
            .launchIn(appScope)
    }

    private suspend fun generateCacheContainer(): PkgData {
        log(TAG) { "generateCacheContainer()..." }
        return try {
            PkgData(pkgMap = gatherPkgData())
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to load pkg data: ${e.asLog()}" }
            PkgData(error = e)
        }
    }

    private suspend fun gatherPkgData(): Map<CacheKey, CachedInfo> {
        log(TAG, INFO) { "generatePkgcache()..." }
        val start = System.currentTimeMillis()
        val leases = setOf(pkgOps, gatewaySwitch, shellOps).map { it.sharedResource.get() }

        val sourceMap: Map<KClass<out PkgDataSource>, Collection<Installed>> = coroutineScope {
            pkgSources.map { source ->
                async {
                    log(TAG) { "generatePkgcache(): $source start..." }
                    val sourceStart = System.currentTimeMillis()
                    val fromSource = source.getPkgs()
                    val sourceStop = System.currentTimeMillis()
                    log(TAG) {
                        "generatePkgcache(): ${fromSource.size} pkgs from $source took ${sourceStop - sourceStart}ms"
                    }
                    source::class to fromSource
                }
            }
        }.awaitAll().toMap()

        val mergedData = mutableMapOf<CacheKey, CachedInfo>()

        // This is our primary source of data, we don't overwrite this data with data from other sources
        sourceMap[NormalPkgsSource::class]!!.forEach { pkg ->
            val key = CacheKey(pkg)
            mergedData[key] = CachedInfo(key, pkg)
        }

        sourceMap
            .filter { it.key != NormalPkgsSource::class }
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

        log(TAG, INFO) { "Pkgs total: ${mergedData.size}" }
        mergedData.values.forEach { log(TAG, DEBUG) { "Installed package: $it" } }
        val stop = System.currentTimeMillis()
        log(TAG, INFO) { "generatePkgcache(): PkgRepo load took ${stop - start}ms" }

        leases.closeAll()

        return mergedData
    }

    suspend fun refresh(): Collection<Installed> {
        val before = cache.value()
        log(TAG) { "refresh()... (before=${before.pkgCount})" }
        val after = cache.updateBlocking { generateCacheContainer() }
        log(TAG, INFO) { "...refresh()ed (after=${after.pkgCount})" }
        return after.pkgs
    }

    private suspend fun queryCache(
        pkgId: Pkg.Id,
        userHandle: UserHandle2?,
    ): Set<CachedInfo> {
        val systemHandle = userManager.systemUser().handle

        val infos = cache.value().pkgMap.filter {
            it.key.pkgId == pkgId && (userHandle == null || userHandle == systemHandle || it.key.userHandle == userHandle)
        }
        if (infos.isNotEmpty()) return infos.values.toSet()

        log(TAG, VERBOSE) { "Cache miss for $pkgId:$userHandle" }

        // Save the cache miss for better performance
        return if (userHandle != null) {
            val key = CacheKey(pkgId, userHandle)
            val cacheInfo = CachedInfo(key, null)

            cache.updateBlocking {
                this.copy(
                    pkgMap = this.pkgMap.mutate {
                        this[key] = cacheInfo
                    }
                )
            }

            setOf(cacheInfo)
        } else {
            emptySet()
        }
    }

    suspend fun query(
        pkgId: Pkg.Id,
        userHandle: UserHandle2?,
    ): Collection<Installed> = queryCache(pkgId, userHandle).mapNotNull { it.data }

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
        private val TAG = logTag("Pkg", "Repo")
    }
}