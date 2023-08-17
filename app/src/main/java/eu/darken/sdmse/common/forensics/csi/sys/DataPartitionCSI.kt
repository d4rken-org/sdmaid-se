package eu.darken.sdmse.common.forensics.csi.sys

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.startsWith
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.priv.PrivateDataCSI
import eu.darken.sdmse.common.forensics.csi.toOwners
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@Reusable
class DataPartitionCSI @Inject constructor(
    private val areaManager: DataAreaManager,
    private val clutterRepo: ClutterRepo,
) : LocalCSIProcessor {

    private var badMatches: Collection<APath>? = null

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.DATA

    override suspend fun identifyArea(target: APath): AreaInfo? {
        val dataAreas = areaManager.currentAreas().filter { it.type == DataArea.Type.DATA }

        if (getBadMatches().any { it.isAncestorOf(target) }) return null

        val matchedArea = dataAreas.singleOrNull { target.startsWith(it.path) } ?: return null

        return AreaInfo(
            dataArea = matchedArea,
            file = target,
            prefix = matchedArea.path,
            isBlackListLocation = false
        )
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        val owners = mutableSetOf<Owner>()

        var bestBet = areaInfo.prefixFreeSegments

        while (bestBet.isNotEmpty()) {
            val matches = clutterRepo.match(areaInfo.type, bestBet)
            owners.addAll(matches.map { it.toOwners(areaInfo) }.flatten())

            if (owners.isNotEmpty()) break

            bestBet = bestBet.dropLast(1)
        }

        return CSIProcessor.Result(
            owners = owners,
        )
    }

    private val cacheLock = Mutex()

    private suspend fun getBadMatches(): Collection<APath> = cacheLock.withLock {
        badMatches?.let { return@withLock it }

        val dataAreas = areaManager.currentAreas()
        val badMatchTypes = setOf(
            DataArea.Type.APP_APP,
            DataArea.Type.APP_APP_PRIVATE,
            DataArea.Type.APP_ASEC,
            DataArea.Type.APP_LIB,
            DataArea.Type.PRIVATE_DATA,
            DataArea.Type.DATA_SYSTEM,
            DataArea.Type.DATA_SYSTEM_DE,
            DataArea.Type.DATA_SYSTEM_CE,
            DataArea.Type.DATA_SDEXT2,
            DataArea.Type.DALVIK_DEX,
            DataArea.Type.DALVIK_PROFILE,
        )

        val part1 = dataAreas
            .filter { badMatchTypes.contains(it.type) }
            .filter { it.hasFlags(DataArea.Flag.PRIMARY) || it.type != DataArea.Type.DATA }
            .map { it.path }

        val part2 = dataAreas
            .filter { it.type == DataArea.Type.DATA && it.hasFlags(DataArea.Flag.PRIMARY) }
            .map { LocalPath.build(it.path.path, PrivateDataCSI.DEFAULT_DIR) }

        (part1 + part2).also {
            badMatches = it
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DataPartitionCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Data")
    }
}