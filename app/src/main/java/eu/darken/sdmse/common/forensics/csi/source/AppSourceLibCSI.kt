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
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.isDescendantOf
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class AppSourceLibCSI @Inject constructor(
    private val areaManager: DataAreaManager,
    private val pkgRepo: PkgRepo,
    private val clutterRepo: ClutterRepo,
) : LocalCSIProcessor {
    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.APP_LIB

    override suspend fun identifyArea(target: APath): AreaInfo? = areaManager.currentAreas()
        .filter { it.type == DataArea.Type.APP_LIB }
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
        val userHandle = areaInfo.userHandle
        val dirName = areaInfo.prefixFreeSegments.first()
        dirName
            .let { APPLIB_DIR.matchEntire(it) }
            ?.let { it.groupValues[1].toPkgId() }
            ?.takeIf { pkgRepo.isInstalled(it, userHandle) }
            ?.let { owners.add(Owner(it, userHandle)) }

        if (owners.isEmpty()) {
            pkgRepo.currentPkgs()
                .filter { it.applicationInfo != null }
                .filter { pkg ->
                    val nativLibDir = pkg.applicationInfo?.nativeLibraryDir?.let {
                        LocalPath.build(it)
                    } ?: return@filter false
                    areaInfo.file == nativLibDir || areaInfo.file.isDescendantOf(nativLibDir)
                }
                .map { Owner(it.id, userHandle) }
                .run { owners.addAll(this) }
        }

        if (owners.isEmpty()) {
            val matches = clutterRepo.match(areaInfo.type, listOf(dirName))
            owners.addAll(matches.map { it.toOwners(areaInfo) }.flatten())
        }


        return CSIProcessor.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AppSourceLibCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "AppSource", "Lib")
        const val DIRNAME = "app-lib"
        private val APPLIB_DIR by lazy { Regex("^([\\w.\\-]+)-[0-9]{1,4}$") }
    }
}