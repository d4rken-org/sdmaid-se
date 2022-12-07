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
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.priv.PrivateDataCSI
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.pathChopOffLast
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

@Reusable
class DataPartitionCSI @Inject constructor(
    private val areaManager: DataAreaManager,
    private val clutterRepo: ClutterRepo,
) : LocalCSIProcessor {

    private var badMatches: Collection<String>? = null

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.DATA

    override suspend fun identifyArea(target: APath): AreaInfo? {
        val dataAreas = areaManager.currentAreas().filter { it.type == DataArea.Type.DATA }

        val matchedArea = dataAreas.singleOrNull { target.path.startsWith(it.path.path) } ?: return null


        if (getBadMatches().any { target.path.startsWith(it) }) return null

        return AreaInfo(
            dataArea = matchedArea,
            file = target,
            prefix = "${matchedArea.path.path}${File.separator}",
            isBlackListLocation = false
        )
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        val owners = mutableSetOf<Owner>()

        var bestBet: String? = areaInfo.file.path.replace(areaInfo.prefix, "")
        if (bestBet?.startsWith(File.separator) == true) bestBet = bestBet.substring(1)

        while (bestBet != null) {
            val matches = clutterRepo.match(areaInfo.type, bestBet)
            owners.addAll(matches.map { it.toOwners() }.flatten())

            if (owners.isNotEmpty()) break

            bestBet = bestBet.pathChopOffLast()
        }

        return CSIProcessor.Result(
            owners = owners,
        )
    }

    private val cacheLock = Mutex()

    private suspend fun getBadMatches(): Collection<String> = cacheLock.withLock {
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
        )

        val part1 = dataAreas
            .filter { badMatchTypes.contains(it.type) }
            .filter { it.hasFlags(DataArea.Flag.PRIMARY) || it.type != DataArea.Type.DATA }
            .map { "${it.path.path}${File.separator}" }

        val part2 = dataAreas
            .filter { it.type == DataArea.Type.DATA && it.hasFlags(DataArea.Flag.PRIMARY) }
            .map { LocalPath.build(it.path.path, PrivateDataCSI.DEFAULT_DIR).path + File.separator }

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