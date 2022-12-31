package eu.darken.sdmse.common.forensics.csi.source

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
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.regex.Pattern
import javax.inject.Inject

@Reusable
class AppSourceAsecCSI @Inject constructor(
    private val areaManager: DataAreaManager,
    private val pkgRepo: PkgRepo,
    private val clutterRepo: ClutterRepo,
) : LocalCSIProcessor {
    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.APP_ASEC

    override suspend fun identifyArea(target: APath): AreaInfo? = areaManager.currentAreas()
        .filter { it.type == DataArea.Type.APP_ASEC }
        .mapNotNull { area ->
            if (!area.path.isAncestorOf(target)) return@mapNotNull null

            AreaInfo(
                dataArea = area,
                file = target,
                prefix = area.path,
                isBlackListLocation = true
            )
        }
        .singleOrNull()

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        val owners = mutableSetOf<Owner>()

        val dirName = areaInfo.prefixFreePath.first()
        dirName
            .let { ASEC_FILE.matcher(it) }
            .takeIf { it.matches() }
            ?.let { it.group(1)?.toPkgId() }
            ?.takeIf { pkgRepo.isInstalled(it) }
            ?.let { owners.add(Owner(it)) }

        if (owners.isEmpty()) {
            val matches = clutterRepo.match(areaInfo.type, listOf(dirName))
            owners.addAll(matches.map { it.toOwners() }.flatten())
        }

        if (owners.isEmpty()) {
            owners.add(Owner(areaInfo.file.name.toPkgId()))
            owners.add(Owner(areaInfo.file.name.replace(".asec", "").toPkgId()))
        }

        return CSIProcessor.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AppSourceAsecCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "AppSource", "Asec")
        const val DIRNAME = "app-asec"
        private val ASEC_FILE = Pattern.compile("^([\\w.\\-]+)(?:\\-[0-9]{1,4}.asec)$")
    }
}