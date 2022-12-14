package eu.darken.sdmse.common.forensics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgRepo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileForensics @Inject constructor(
    @ApplicationContext val context: Context,
    private val pkgRepo: PkgRepo,
    private val csiProcessors: Set<@JvmSuppressWildcards CSIProcessor>,
) {

    init {
        log(TAG, INFO) { "${csiProcessors.size} CSI processors loaded." }
    }

    suspend fun identifyArea(file: APath): AreaInfo {
        if (file is LocalPath && !file.file.isAbsolute) throw IllegalArgumentException("Not absolute: ${file.path}")

        return csiProcessors.firstNotNullOf { it.identifyArea(file) }
    }

    suspend fun findOwners(file: APath): OwnerInfo {
        if (file is LocalPath && !file.file.isAbsolute) throw IllegalArgumentException("Not absolute:" + file.path)

        return findOwners(identifyArea(file))
    }

    suspend fun findOwners(areaInfo: AreaInfo): OwnerInfo {
        val startFindingOwner = System.currentTimeMillis()

        val result = csiProcessors
            .firstOrNull { it.hasJurisdiction(areaInfo.type) }
            ?.findOwners(areaInfo)
            ?: CSIProcessor.Result().also {
                log(TAG, WARN) { "No CSI processor has juridiction: $areaInfo" }
                if (Bugs.isDebug) throw IllegalStateException("Missing CSI processor")
            }

        val installedOwners = result.owners.filter { pkgRepo.isInstalled(it.pkgId) }.toSet()

        val ownerInfo = OwnerInfo(
            areaInfo = areaInfo,
            owners = result.owners,
            installedOwners = installedOwners,
            hasUnknownOwner = result.hasKnownUnknownOwner,
        )

        val timeForProcessing = System.currentTimeMillis() - startFindingOwner

        if (Bugs.isDebug) {
            time += timeForProcessing
            count++
            val avg = time / count
            log(TAG, VERBOSE) { "Processing: ${timeForProcessing}ms, avg. ${avg}ms ($areaInfo)" }
            for (own in ownerInfo.owners) log(TAG, VERBOSE) { "Matched $areaInfo to ${own.pkgId}" }
            if (ownerInfo.hasUnknownOwner) log(TAG, VERBOSE) { "$areaInfo has an unknown Owner" }
        }

        return ownerInfo
    }

    private var time: Long = 0
    private var count: Long = 0

    companion object {
        val TAG: String = logTag("CSI", "FileForensics")
    }
}