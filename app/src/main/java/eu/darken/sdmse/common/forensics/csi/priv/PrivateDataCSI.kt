package eu.darken.sdmse.common.forensics.csi.priv

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
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.getFirstDirElement
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.user.UserManager2
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

@Reusable
class PrivateDataCSI @Inject constructor(
    private val pkgRepo: PkgRepo,
    private val pkgOps: PkgOps,
    private val areaManager: DataAreaManager,
    private val clutterRepo: ClutterRepo,
    private val userManager: UserManager2,
    private val storageEnvironment: StorageEnvironment,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.PRIVATE_DATA

    override suspend fun identifyArea(target: APath): AreaInfo? {
        var userPrimary: DataArea? = null
        for (area in areaManager.currentAreas().filter { it.type == DataArea.Type.PRIVATE_DATA }) {
            val base = area.path
            if (userManager.currentUser === area.userHandle) {
                userPrimary = area
            }
            if (target.path.startsWith(base.path + File.separator) && target.path != base.path
            ) {
                return AreaInfo(
                    dataArea = area,
                    file = target,
                    prefix = "${base.path}${File.separator}",
                    isBlackListLocation = true,
                )
            }
        }
        if (userPrimary != null) {
            // TODO on Android11+ this matches the data_mirror overlay in /data/data, evaluate whether we should keep matching this
            val defaultPrivateDataDir = File(storageEnvironment.dataDir.path, DEFAULT_DIR)
            if (target.path.startsWith("${defaultPrivateDataDir.path}${File.separator}") && target.path != defaultPrivateDataDir.path) {
                return AreaInfo(
                    userPrimary,
                    target,
                    "${defaultPrivateDataDir.path}${File.separator}",
                    true,
                )
            }
        }
        return null
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        val owners = mutableSetOf<Owner>()

        val dirName = areaInfo.prefixFreePath.getFirstDirElement()

        if (pkgRepo.isInstalled(dirName.toPkgId())) {
            owners.add(Owner(dirName.toPkgId()))
        }

        val hiddenPkg = tryCleanName(dirName)
        if (owners.isEmpty()) {
            if (hiddenPkg != null && pkgRepo.isInstalled(hiddenPkg.toPkgId())) {
                owners.add(Owner(hiddenPkg.toPkgId()))
            }
        }

        if (owners.isEmpty()) {
            // Once it's no longer a default folder name and match, we need to find all possible owners to protect against false positive corpses
            val matches = clutterRepo.match(areaInfo.type, dirName)
            owners.addAll(matches.map { it.toOwners() }.flatten())
        }

        // Fallback, no downside to assuming that dirname=pkgname for PRIVATE_DATA if there are no other owners
        if (owners.isEmpty()) {
            owners.add(Owner((hiddenPkg ?: dirName).toPkgId()))
        }

        return CSIProcessor.Result(
            owners = owners
        )
    }

    private suspend fun tryCleanName(currentName: String): String? {
        if (currentName.startsWith(".external.")) {
            // rare modifier, seen it on my N5 with the .external.com.plexapp.android
            return currentName.substring(10)
        } else if (currentName.startsWith("_") || currentName.startsWith(".")) {
            // usually used in public/Android/data to hide stuff from uninstall
            // some devs just down know better
            return currentName.substring(1)
        } else if (currentName.startsWith("com.lge.theme.")) {
            // https://github.com/d4rken/sdmaid-public/issues/615
            val matcher = LGE_THEME_PATTERN.matcher(currentName)
            if (matcher.matches()) {
                return matcher.group(1)
            }
        } else if (currentName.endsWith(".overlay")) {
            val ownerPkg = currentName.substring(0, currentName.lastIndexOf(".overlay"))
            val apk = LocalPath.build("system", "vendor", "overlay", ownerPkg, "$ownerPkg.apk")
            val info = pkgOps.viewArchive(apk, 0)
            if (info != null && info.packageName == currentName) {
                return ownerPkg
            }
        }
        return null
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PrivateDataCSI): CSIProcessor
    }

    companion object {
        val TAG: String = logTag("CSI", "Private", "AppData")
        const val DEFAULT_DIR = "data"
        private val LGE_THEME_PATTERN = Pattern.compile("^(com\\.lge\\.theme\\.[\\w_\\-]+)(\\.[\\w\\._\\-]+)$")
    }
}