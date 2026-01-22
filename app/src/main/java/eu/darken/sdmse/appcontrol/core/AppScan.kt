package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.appcontrol.core.archive.ArchiveSupport
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
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.isArchived
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class AppScan @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val pkgRepo: PkgRepo,
    private val pkgOps: PkgOps,
    private val usageTool: UsageTool,
    private val userManager: UserManager2,
    private val archiveSupport: ArchiveSupport,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(PkgOps.TAG, appScope + dispatcherProvider.IO)

    private val mutex = Mutex()
    private var activeCache: Map<InstallId, Boolean?>? = null
    private var sizeCache: Map<InstallId, PkgOps.SizeStats?>? = null
    private var usageCache: Map<InstallId, UsageInfo?>? = null
    private var userProfilesCache: Map<UserHandle2, UserProfile2>? = null

    private suspend fun <T> doRun(action: suspend () -> T): T = mutex.withLock {
        adoptChildResource(pkgOps.sharedResource)
        action()
    }

    suspend fun refresh() = doRun {
        activeCache = null
        sizeCache = null
        usageCache = null
        userProfilesCache = null
        pkgRepo.refresh()
    }

    private suspend fun getUsage(id: InstallId): UsageInfo? {
        if (usageCache == null) {
            usageCache = mutableMapOf<InstallId, UsageInfo?>().apply {
                putAll(usageTool.lastMonth().map { it.installId to it })
            }
        }
        return usageCache!![id]
    }

    private suspend fun getSize(id: InstallId): PkgOps.SizeStats? {
        return sizeCache?.get(id) ?: pkgOps.querySizeStats(id)
    }

    private suspend fun getActive(id: InstallId): Boolean? {
        if (activeCache == null) {
            activeCache = mutableMapOf<InstallId, Boolean?>().apply {
                putAll(pkgOps.getRunningPackages().map { it to true })
            }
        }
        return activeCache!![id]
    }

    private suspend fun getUserProfile(id: InstallId): UserProfile2? {
        if (userProfilesCache == null) {
            userProfilesCache = userManager.allUsers().associateBy { it.handle }
        }
        return userProfilesCache!![id.userHandle]
    }

    suspend fun allApps(
        user: UserHandle2?,
        includeUsage: Boolean,
        includeActive: Boolean,
        includeSize: Boolean,
    ): Set<AppInfo> = doRun {
        log(TAG, VERBOSE) { "allApps(user=$user)" }
        val pkgs = pkgRepo.current().filter { user == null || it.userHandle == user }

        if (sizeCache == null) {
            sizeCache = pkgs
                .map { it.installId }
                .asFlow()
                .flatMapMerge(4) {
                    flow { emit(it to pkgOps.querySizeStats(it)) }
                }
                .toList().associate { (id, size) -> id to size }
        }

        pkgs.map {
            it.toAppInfo(
                includeUsage = includeUsage,
                includeActive = includeActive,
                includeSize = includeSize,
                includeUserProfiles = user == null,
            )
        }.toSet()
    }

    suspend fun app(
        pkgId: Pkg.Id,
        user: UserHandle2?,
        includeUsage: Boolean,
        includeActive: Boolean,
        includeSize: Boolean,
    ): Set<AppInfo> = doRun {
        log(TAG, VERBOSE) { "app($pkgId, user=$user)" }

        pkgRepo.current()
            .filter { it.id == pkgId }
            .filter { user == null || it.userHandle == user }
            .map {
                it.toAppInfo(
                    includeUsage = includeUsage,
                    includeActive = includeActive,
                    includeSize = includeSize,
                    includeUserProfiles = user == null,
                )
            }
            .toSet()
    }


    private suspend fun Installed.toAppInfo(
        includeActive: Boolean,
        includeSize: Boolean,
        includeUsage: Boolean,
        includeUserProfiles: Boolean,
    ) = AppInfo(
        pkg = this,
        isActive = if (includeActive) getActive(installId) ?: false else null,
        sizes = if (includeSize) getSize(installId) else null,
        usage = if (includeUsage) getUsage(installId) else null,
        userProfile = if (includeUserProfiles) getUserProfile(installId) else null,
        canBeToggled = this is NormalPkg,
        canBeStopped = this is NormalPkg,
        canBeExported = this is SourceAvailable,
        canBeDeleted = true,
        canBeArchived = this is NormalPkg &&
                archiveSupport.isArchivable(this) &&
                !isArchived &&
                !isSystemApp,
    )

    companion object {
        private val TAG = logTag("AppControl", "AppScan")
    }
}