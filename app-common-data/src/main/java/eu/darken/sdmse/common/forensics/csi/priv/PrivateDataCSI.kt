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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.LocalCSIProcessor
import eu.darken.sdmse.common.forensics.csi.toOwners
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.getPkgsForUid
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@Reusable
class PrivateDataCSI @Inject constructor(
    private val pkgRepo: PkgRepo,
    private val pkgOps: PkgOps,
    private val areaManager: DataAreaManager,
    private val clutterRepo: ClutterRepo,
    private val userManager: UserManager2,
    private val storageEnvironment: StorageEnvironment,
    private val gatewaySwitch: GatewaySwitch,
) : LocalCSIProcessor {

    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.PRIVATE_DATA

    override suspend fun identifyArea(target: APath): AreaInfo? {
        var userPrimary: DataArea? = null
        for (area in areaManager.currentAreas().filter { it.type == DataArea.Type.PRIVATE_DATA }) {

            if (userManager.currentUser().handle == area.userHandle) {
                userPrimary = area
            }
            if (area.path.isAncestorOf(target)) {
                return AreaInfo(
                    dataArea = area,
                    file = target,
                    prefix = area.path,
                    isBlackListLocation = true,
                )
            }
        }
        if (userPrimary != null) {
            // TODO on Android11+ this matches the data_mirror overlay in /data/data, evaluate whether we should keep matching this
            val defaultPrivateDataDir = storageEnvironment.dataDir.child(DEFAULT_DIR)
            if (defaultPrivateDataDir.isAncestorOf(target)) {
                return AreaInfo(
                    file = target,
                    prefix = defaultPrivateDataDir,
                    dataArea = userPrimary,
                    isBlackListLocation = true,
                )
            }
        }
        return null
    }

    override suspend fun findOwners(areaInfo: AreaInfo): CSIProcessor.Result {
        require(hasJurisdiction(areaInfo.type)) { "Wrong jurisdiction: ${areaInfo.type}" }

        val owners = mutableSetOf<Owner>()
        var hasUnknownOwner = false

        val userHandle = areaInfo.userHandle
        val dirName = areaInfo.prefixFreeSegments.first()

        if (pkgRepo.isInstalled(dirName.toPkgId(), userHandle)) {
            owners.add(Owner(dirName.toPkgId(), userHandle))
        }

        val hiddenPkg = tryCleanName(dirName)
        if (owners.isEmpty()) {
            if (hiddenPkg != null && pkgRepo.isInstalled(hiddenPkg.toPkgId(), userHandle)) {
                owners.add(Owner(hiddenPkg.toPkgId(), userHandle))
            }
        }

        if (owners.isEmpty()) {
            // Once it's no longer a default folder name and match, we need to find all possible owners to protect against false positive corpses
            val matches = clutterRepo.match(areaInfo.type, listOf(dirName))
            owners.addAll(matches.map { it.toOwners(areaInfo) }.flatten())
        }

        // No currently-installed owner found by name/clutter (clutter matches are not verified
        // installed, hence the re-check). The directory may still be actively maintained by a live
        // package (e.g. a renamed system service writing to its legacy data path, like
        // com.samsung.android.wifi.intelligence -> com.samsung.android.wifi.ai). Attribute by the
        // directory's POSIX owner uid before assuming it's a corpse. This costs one extra root stat
        // per corpse-suspect dir, which is bounded (top-level dirs only).
        if (owners.none { pkgRepo.isInstalled(it.pkgId, userHandle) }) {
            when (val resolved = resolveOwnersByUid(areaInfo)) {
                is UidOwners.Single -> owners.add(resolved.owner)
                UidOwners.Shared -> hasUnknownOwner = true
                UidOwners.Unknown -> hasUnknownOwner = true
                UidOwners.None -> {}
            }
        }

        // Fallback, no downside to assuming that dirname=pkgname for PRIVATE_DATA if there are no other owners
        if (owners.isEmpty() && !hasUnknownOwner) {
            owners.add(Owner((hiddenPkg ?: dirName).toPkgId(), userHandle))
        }

        return CSIProcessor.Result(
            owners = owners,
            hasKnownUnknownOwner = hasUnknownOwner,
        )
    }

    /**
     * Resolves the directory's POSIX owner uid to the live package(s) holding it. A uid (especially a
     * shared system uid like 1000) can map to many packages; in that case we can't name a single owner
     * but the data is clearly not orphaned, so we report it as a known-but-unidentified owner. If the
     * lookup fails outright we can't tell either way, so we fail safe and also treat it as owned rather
     * than risk reporting a live app's data as a corpse.
     */
    private suspend fun resolveOwnersByUid(areaInfo: AreaInfo): UidOwners {
        val localPath = areaInfo.file as? LocalPath ?: return UidOwners.None

        val uid = try {
            gatewaySwitch.lookupExtended(localPath).ownership?.userId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "resolveOwnersByUid($areaInfo) failed: ${e.asLog()}" }
            // A lookup failure is not evidence the owner is gone. Stay conservative and keep the data
            // protected (known-but-unidentified owner) rather than risk reporting a live app's data as a
            // corpse because of a transient root/IPC/stat error.
            return UidOwners.Unknown
        }
            // No owner uid despite a successful lookup (e.g. stat couldn't resolve it) is equally
            // inconclusive, so fail safe rather than fall through to the dirname corpse fallback.
            ?: return UidOwners.Unknown

        // A per-app uid maps to exactly one package and its user matches the area; a shared system uid
        // (e.g. 1000) maps to many, so we can't name a single owner but the data is clearly not orphaned.
        val livePkgs = pkgRepo.getPkgsForUid(uid)
        return when {
            livePkgs.isEmpty() -> UidOwners.None
            // Attribute to the matched package's own user, not the area's: getPkgsForUid resolved the
            // package from the uid's decomposed user, and the two can differ for cross-user data.
            livePkgs.size == 1 -> livePkgs.first().let { UidOwners.Single(Owner(it.id, it.userHandle)) }
            else -> UidOwners.Shared
        }
    }

    private sealed interface UidOwners {
        /** A successful lookup whose uid maps to no live package; the dirname corpse fallback may apply. */
        object None : UidOwners

        /** Exactly one live package owns the data. */
        data class Single(val owner: Owner) : UidOwners

        /** A shared uid maps to several live packages: owned, but no single owner can be named. */
        object Shared : UidOwners

        /** The owner uid could not be determined (lookup failure); inconclusive, so fail safe. */
        object Unknown : UidOwners
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
            val matcher = LGE_THEME_PATTERN.matchEntire(currentName)
            if (matcher != null) {
                return matcher.groupValues[1]
            }
        } else if (currentName.endsWith(".overlay")) {
            val ownerPkg = currentName.substring(0, currentName.lastIndexOf(".overlay"))
            val targets = setOf(
                LocalPath.build("system", "vendor", "overlay", ownerPkg, "$ownerPkg.apk"),
                // lge/lucye_global_com/lucye:8.0.0/OPR1.170623.032/191911309bbbd:user/release-keys
                LocalPath.build("/OP/OPEN_EU/overlay/app/$ownerPkg.apk"),
            )
            targets.forEach {
                val info = pkgOps.viewArchive(it, 0)
                if (info != null && info.packageName == currentName) {
                    return ownerPkg
                }
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
        private val LGE_THEME_PATTERN by lazy { Regex("^(com\\.lge\\.theme\\.[\\w_\\-]+)(\\.[\\w._\\-]+)$") }
    }
}