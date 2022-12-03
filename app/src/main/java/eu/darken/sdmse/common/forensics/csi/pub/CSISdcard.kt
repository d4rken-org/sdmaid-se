package eu.darken.sdmse.common.forensics.csi.pub

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.pathChopOffLast
import eu.darken.sdmse.common.pkgs.PkgRepo
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

@Reusable
class CSISdcard @Inject constructor(
    private val clutterRepo: ClutterRepo,
    private val pkgRepo: PkgRepo,
    private val areaManager: DataAreaManager,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.SDCARD

    override suspend fun matchLocation(target: APath): AreaInfo? {
        val targetPath: String = target.path
        val dataAreas = areaManager.areas.first()
            .filter { it.type == DataArea.Type.SDCARD }
            .sortedWith(DEEPEST_NEST_FIRST)

        var primary: DataArea? = null
        for (area in dataAreas) {
            if (area.hasFlags(DataArea.Flag.PRIMARY)) primary = area
            val base: String = area.path.path
            if (targetPath.startsWith(base + File.separator) && targetPath != base) {
                // This sdcard fits and it should be the most specific due to sorting.
                return if (!targetPath.startsWith(base + CSIPublicObb.PUBLIC_OBB)
                    && !targetPath.startsWith(base + CSIPublicData.PUBLIC_DATA)
                    && !targetPath.startsWith(base + CSIPublicMedia.ANDROID_MEDIA)
                ) {
                    AreaInfo(
                        file = target,
                        prefix = base + File.separator,
                        isBlackListLocation = false,
                        dataArea = area,
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

        return if (target.path.startsWith(LEGACY_PATH.path + File.separator) && targetPath != LEGACY_PATH.path
            && !targetPath.startsWith(File(LEGACY_PATH, CSIPublicObb.PUBLIC_OBB).path)
            && !targetPath.startsWith(File(LEGACY_PATH, CSIPublicData.PUBLIC_DATA).path)
            && !targetPath.startsWith(File(LEGACY_PATH, CSIPublicMedia.ANDROID_MEDIA).path)
        ) {
            AreaInfo(
                file = target,
                prefix = LEGACY_PATH.path + File.separator,
                isBlackListLocation = false,
                dataArea = primary
            )
        } else null
    }

    override suspend fun process(item: APath, areaInfo: AreaInfo): CSIProcessor.Result {
        // Chop down path till we get a hit
        var bestBet: String? = item.path.replace(areaInfo.prefix, "")
        if (bestBet!!.startsWith(File.separator)) bestBet = bestBet.substring(1)
        val owners = mutableSetOf<Owner>()
        while (bestBet != null) {
            val matches = clutterRepo.match(areaInfo.dataArea.type, bestBet)
            val newOwners = matches
                .map { match -> match.packageNames.map { it to match.flags } }
                .flatten()
                .map { (pkg, flags) -> Owner(pkg, flags, pkgRepo.isInstalled(pkg)) }

            owners.addAll(newOwners)
            bestBet = if (owners.isEmpty()) {
                bestBet.pathChopOffLast()
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
        @Binds @IntoSet abstract fun mod(mod: CSISdcard): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Sdcard")
        private val LEGACY_PATH = File("/storage/emulated/legacy/")

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