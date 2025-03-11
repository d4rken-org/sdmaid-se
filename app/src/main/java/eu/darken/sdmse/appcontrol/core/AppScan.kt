package eu.darken.sdmse.appcontrol.core

import android.app.usage.UsageStatsManager
import eu.darken.sdmse.appcontrol.core.usage.UsageInfo
import eu.darken.sdmse.appcontrol.core.usage.UsageTool
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.current
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class AppScan @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val statsManager: UsageStatsManager,
    private val pkgRepo: PkgRepo,
    private val pkgOps: PkgOps,
    private val usageTool: UsageTool,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(PkgOps.TAG, appScope + dispatcherProvider.IO)

    private val mutex = Mutex()
    private val activeCache = mutableMapOf<Installed.InstallId, Boolean?>()
    private val sizeCache = mutableMapOf<Installed.InstallId, PkgOps.SizeStats?>()
    private val usageCache = mutableMapOf<Installed.InstallId, UsageInfo?>()

    suspend fun refresh() = mutex.withLock {
        activeCache.clear()
        sizeCache.clear()
        usageCache.clear()
        pkgRepo.refresh()
    }

    suspend fun allApps(
        user: UserHandle2,
        includeUsage: Boolean,
        includeActive: Boolean,
        includeSize: Boolean,
    ): Set<AppInfo> = mutex.withLock {
        log(TAG, VERBOSE) { "allApps($user)" }
        val pkgs = pkgRepo.current()
        return pkgs
            .filter { it.userHandle == user }
            .map {
                it.toAppInfo(
                    includeUsage = includeUsage,
                    includeActive = includeActive,
                    includeSize = includeSize,
                )
            }
            .toSet()
    }

    suspend fun app(
        installId: Installed.InstallId,
        includeUsage: Boolean,
        includeActive: Boolean,
        includeSize: Boolean,
    ): AppInfo = mutex.withLock {
        log(TAG, VERBOSE) { "app($installId)" }
        pkgRepo.current()
            .single { it.installId == installId }
            .toAppInfo(
                includeUsage = includeUsage,
                includeActive = includeActive,
                includeSize = includeSize,
            )
    }

    suspend fun app(
        pkgId: Pkg.Id,
        includeUsage: Boolean,
        includeActive: Boolean,
        includeSize: Boolean,
    ): Set<AppInfo> = mutex.withLock {
        log(TAG, VERBOSE) { "app($pkgId)" }
        pkgRepo.current()
            .filter { it.id == pkgId }
            .map {
                it.toAppInfo(
                    includeUsage = includeUsage,
                    includeActive = includeActive,
                    includeSize = includeSize,
                )
            }
            .toSet()
    }


    private suspend fun Installed.toAppInfo(
        includeActive: Boolean,
        includeSize: Boolean,
        includeUsage: Boolean,
    ): AppInfo = AppInfo(
        pkg = this,
        isActive = if (includeActive) {
            activeCache[installId] ?: pkgOps.isRunning(installId).also { activeCache[installId] = it }
        } else null,
        sizes = if (includeSize) {
            sizeCache[installId] ?: pkgOps.querySizeStats(installId).also { sizeCache[installId] = it }
        } else null,
        usage = if (includeUsage) {
            if (usageCache.isEmpty()) usageCache.putAll(usageTool.lastMonth().map { it.installId to it })
            usageCache[installId]
        } else null,
        canBeToggled = this is NormalPkg,
        canBeStopped = this is NormalPkg,
        canBeExported = this is SourceAvailable,
        canBeDeleted = true,
    )

    companion object {
        private val TAG = logTag("AppControl", "AppScan")
    }
}