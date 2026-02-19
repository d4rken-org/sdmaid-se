package eu.darken.sdmse.common.forensics.csi.pub

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject


@Reusable
class SdcardCSI @Inject constructor(
    private val clutterRepo: ClutterRepo,
    private val areaManager: DataAreaManager,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.SDCARD

    @Suppress("SimplifiableCallChain")
    override suspend fun identifyArea(target: APath): AreaInfo? {
        return areaManager.currentAreas()
            .filter { it.type == DataArea.Type.SDCARD }
            .sortedWith(DEEPEST_NEST_FIRST)
            .filter { it.path.isAncestorOf(target) }
            .filter { area ->
                val overlaps = setOf(
                    area.path.child(*PublicObbCSI.BASE_SEGMENTS),
                    area.path.child(*PublicDataCSI.BASE_SEGMENTS),
                    area.path.child(*PublicMediaCSI.BASE_SEGMENTS),
                )
                overlaps.none { it.isAncestorOf(target) }
            }
            .singleOrNull()
            ?.let {
                AreaInfo(
                    dataArea = it,
                    file = target,
                    prefix = it.path,
                    isBlackListLocation = false,
                )
            }
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        // Chop down path till we get a hit
        var bestBet = areaInfo.prefixFreeSegments
        val owners = mutableSetOf<Owner>()
        while (bestBet.isNotEmpty()) {
            val matches = clutterRepo.match(areaInfo.type, bestBet)
            val newOwners = matches
                .map { match -> match.packageNames.map { it to match.flags } }
                .flatten()
                .map { (pkg, flags) -> Owner(pkg, areaInfo.userHandle, flags) }

            owners.addAll(newOwners)
            bestBet = if (owners.isEmpty()) {
                bestBet.dropLast(1)
            } else {
                break
            }
        }
        return CSIProcessor.Result(
            owners = owners,
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SdcardCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Sdcard")

        val DEEPEST_NEST_FIRST: Comparator<DataArea> = Comparator<DataArea> { object1: DataArea, object2: DataArea ->
            val nestLevel1: Int = object1.path.path.split(Pattern.quote(File.separator)).size
            val nextLevel2: Int = object2.path.path.split(Pattern.quote(File.separator)).size
            when {
                nextLevel2 > nestLevel1 -> 1
                nextLevel2 < nestLevel1 -> -1
                else -> 0
            }
        }
    }
}