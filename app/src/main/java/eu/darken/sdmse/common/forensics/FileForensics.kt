package eu.darken.sdmse.common.forensics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileForensics @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext val context: Context,
    private val pkgRepo: PkgRepo,
    private val csiProcessors: Set<@JvmSuppressWildcards CSIProcessor>,
    gatewaySwitch: GatewaySwitch,
    pkgOps: PkgOps,
) : HasSharedResource<Any> {

    private val commonResources = setOf(gatewaySwitch, pkgOps)

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    init {
        log(TAG, INFO) { "${csiProcessors.size} CSI processors loaded." }
        log(TAG, VERBOSE) { csiProcessors.joinToString("\n") }
    }

    suspend fun identifyArea(file: APath): AreaInfo? = keepResourceHoldersAlive(commonResources) {
        if (file is LocalPath && !file.file.isAbsolute) throw IllegalArgumentException("Not absolute: ${file.path}")

        csiProcessors.firstNotNullOfOrNull { it.identifyArea(file) }
    }

    suspend fun findOwners(file: APath): OwnerInfo? {
        if (file is LocalPath && !file.file.isAbsolute) throw IllegalArgumentException("Not absolute:" + file.path)

        return identifyArea(file)?.let { findOwners(it) }
    }

    suspend fun findOwners(areaInfo: AreaInfo): OwnerInfo? = keepResourceHoldersAlive(commonResources) {
        val startFindingOwner = System.currentTimeMillis()

        val result = csiProcessors
            .firstOrNull { it.hasJurisdiction(areaInfo.type) }
            ?.findOwners(areaInfo)
            ?: CSIProcessor.Result().also {
                log(TAG, WARN) { "No CSI processor has juridiction: $areaInfo" }
                if (Bugs.isDebug) throw IllegalStateException("Missing CSI processor")
            }

        val installedOwners = result.owners.filter {
            pkgRepo.isInstalled(it.pkgId, areaInfo.userHandle)
        }.toSet()

        val ownerInfo = OwnerInfo(
            areaInfo = areaInfo,
            owners = result.owners,
            installedOwners = installedOwners,
            hasUnknownOwner = result.hasKnownUnknownOwner,
        )

        val timeForProcessing = System.currentTimeMillis() - startFindingOwner

        if (Bugs.isTrace) {
            time += timeForProcessing
            count++
            val avg = time / count
            log(TAG, VERBOSE) { "Processing: ${timeForProcessing}ms, avg. ${avg}ms ($areaInfo)" }
            for (own in ownerInfo.owners) log(TAG, VERBOSE) { "Matched ${areaInfo.file} to ${own.pkgId}" }
            if (ownerInfo.hasUnknownOwner) log(TAG, VERBOSE) { "$areaInfo has an unknown Owner" }
        }

        ownerInfo
    }

    private var time: Long = 0
    private var count: Long = 0

    companion object {
        val TAG: String = logTag("CSI", "FileForensics")
    }
}