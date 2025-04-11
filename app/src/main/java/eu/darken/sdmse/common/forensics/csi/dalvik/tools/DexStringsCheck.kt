package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.get
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import javax.inject.Inject

@Reusable
class DexStringsCheck @Inject constructor(
    private val shellOps: ShellOps,
    private val pkgRepo: PkgRepo,
) : DalvikCheck {

    suspend fun check(areaInfo: AreaInfo): DalvikCheck.Result? {
        val path: String = areaInfo.file.path
        val dexTarget = when {
            path.endsWith(".dex") -> path
            path.endsWith(".vdex") -> path.removeSuffix(".vdex") + ".dex"
            path.endsWith(".art") -> path.removeSuffix(".art") + ".dex"
            else -> null
        }
        if (dexTarget == null) {
            log(TAG, WARN) { "Couldn't map file suffix of $path" }
            return null
        }

        val cmd = ShellOpsCmd("strings $dexTarget | grep -m1 -- '--comments=' | sed 's/.*--comments=//'")
        val result = shellOps.execute(cmd, ShellOps.Mode.ROOT)
        log(TAG, VERBOSE) { "strings-grep result: $result" }
        if (!result.isSuccess) return null

        if (result.errors.any { it.contains("No such file or directory") }) {
            log(TAG, WARN) { ".vdex without .dex: $path" }
            return DalvikCheck.Result(hasKnownUnknownOwner = true)
        }

        if (result.output.size != 1) {
            log(TAG, WARN) { "Successful but no output for $dexTarget $result" }
            return null
        }

        val keyMap = result.output.single().split(",").associate {
            val (key, value) = it.split(":")
            key to value
        }

        val pkgId = keyMap["app-name"]?.toPkgId() ?: return null
        val pkg = pkgRepo.get(pkgId, areaInfo.userHandle) ?: return null

        val versionCode = keyMap["app-version-code"]?.toLong()
        if (versionCode != null && pkg.versionCode != versionCode) {
            log(TAG, WARN) { "Version missmatch dex=$versionCode, installed=${pkg.versionCode}" }
        }

        log(TAG) { "Resolved $path to $pkg" }
        return DalvikCheck.Result(setOf(Owner(pkg.id, areaInfo.userHandle)))
    }

    companion object {
        val TAG: String = logTag("CSI", "Dalvik", "Dex", "StringsCheck")
    }
}