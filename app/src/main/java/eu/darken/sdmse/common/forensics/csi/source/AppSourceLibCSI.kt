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
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.getFirstDirElement
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.currentPkgs
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File
import java.util.regex.Pattern
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
            val base = "${area.path.path}${File.separator}"
            if (!target.path.startsWith(base)) return@mapNotNull null

            AreaInfo(
                dataArea = area,
                file = target,
                prefix = base,
                isBlackListLocation = true
            )
        }
        .singleOrNull()

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        val owners = mutableSetOf<Owner>()

        val dirName = areaInfo.prefixFreePath.getFirstDirElement()
        dirName
            .let { APPLIB_DIR.matcher(it) }
            .takeIf { it.matches() }
            ?.let { it.group(1)?.toPkgId() }
            ?.takeIf { pkgRepo.isInstalled(it) }
            ?.let { owners.add(Owner(it)) }

        if (owners.isEmpty()) {
            pkgRepo.currentPkgs()
                .filter { it.packageInfo.applicationInfo != null }
                .filter {
                    val targetPath = areaInfo.file.path
                    val nativLibDir = it.packageInfo.applicationInfo.nativeLibraryDir
                    targetPath == nativLibDir || targetPath.startsWith("${nativLibDir}${File.separator}")
                }
                .map { Owner(it.id) }
                .run { owners.addAll(this) }
        }

        if (owners.isEmpty()) {
            val matches = clutterRepo.match(areaInfo.type, dirName)
            owners.addAll(matches.map { it.toOwners() }.flatten())
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
        private val APPLIB_DIR = Pattern.compile("^([\\w.\\-]+)(?:\\-[0-9]{1,4})$")
    }
}