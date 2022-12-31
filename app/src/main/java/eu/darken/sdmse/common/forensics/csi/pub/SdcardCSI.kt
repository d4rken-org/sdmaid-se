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
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.common.files.core.local.LocalPath
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

    override suspend fun identifyArea(target: APath): AreaInfo? {
        val dataAreas = areaManager.currentAreas()
            .filter { it.type == DataArea.Type.SDCARD }
            .sortedWith(DEEPEST_NEST_FIRST)

        var primary: DataArea? = null
        for (area in dataAreas) {
            if (area.hasFlags(DataArea.Flag.PRIMARY)) primary = area

            if (area.path.isAncestorOf(target)) {
                val overlaps = setOf(
                    area.path.child(*PublicObbCSI.BASE_SEGMENTS),
                    area.path.child(*PublicDataCSI.BASE_SEGMENTS),
                    area.path.child(*PublicMediaCSI.BASE_SEGMENTS),
                )
                // This sdcard fits and it should be the most specific due to sorting.
                return if (overlaps.none { it.isAncestorOf(target) }) {
                    AreaInfo(
                        dataArea = area,
                        file = target,
                        prefix = area.path,
                        isBlackListLocation = false,
                    )
                } else {
                    // The sdcard fit but it wasn't an sdcard location
                    // i.e. /mnt/sdcard/external_sd/Android/data/<pkg>
                    null
                }
            }
        }
        // If we can't find a primary sdcard, the legacy pathes etc. are no good.
        if (primary == null) return null

        val legacyOverlaps = setOf(
            LEGACY_PATH.child(*PublicObbCSI.BASE_SEGMENTS),
            LEGACY_PATH.child(*PublicDataCSI.BASE_SEGMENTS),
            LEGACY_PATH.child(*PublicMediaCSI.BASE_SEGMENTS),
        )

        return if (LEGACY_PATH.isAncestorOf(target) && legacyOverlaps.none { it.isAncestorOf(target) }) {
            AreaInfo(
                dataArea = primary,
                file = target,
                prefix = LEGACY_PATH,
                isBlackListLocation = false
            )
        } else null
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        // Chop down path till we get a hit
        var bestBet = areaInfo.prefixFreePath
        val owners = mutableSetOf<Owner>()
        while (bestBet.isNotEmpty()) {
            val matches = clutterRepo.match(areaInfo.dataArea.type, bestBet)
            val newOwners = matches
                .map { match -> match.packageNames.map { it to match.flags } }
                .flatten()
                .map { (pkg, flags) -> Owner(pkg, flags) }

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
        val LEGACY_PATH = LocalPath.build("storage", "emulated", "legacy")

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