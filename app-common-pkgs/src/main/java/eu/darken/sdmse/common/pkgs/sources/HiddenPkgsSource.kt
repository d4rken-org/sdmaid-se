package eu.darken.sdmse.common.pkgs.sources

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgDataSource
import eu.darken.sdmse.common.pkgs.container.HiddenPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class HiddenPkgsSource @Inject constructor(
    private val pkgOps: PkgOps,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val userManager: UserManager2,
    private val shellOps: ShellOps,
) : PkgDataSource {

    override suspend fun getPkgs(): Collection<Installed> = pkgOps.useRes {
        log(TAG) { "getPkgs()" }

        val mode = when {
            rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
            adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
            else -> null
        }

        if (mode == null) {
            log(TAG) { "Requires root or adb, skipping..." }
            return@useRes emptySet()
        }

        log(TAG) { "Using shell mode $mode" }

        val alreadyKnown = try {
            pkgOps.queryPkgs(0).map { it.packageName }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to grab default packages: ${e.asLog()}" }
            emptySet()
        }

        userManager.allUsers()
            .mapNotNull { user ->
                val result = shellOps.execute(
                    ShellOpsCmd("pm list packages -f --user ${user.handle.handleId}"),
                    mode,
                )
                if (!result.isSuccess) return@mapNotNull null
                user to result.output
            }
            .map { (user, lines) ->
                log(TAG, VERBOSE) { "${lines.size} entries for $user" }
                lines.mapNotNull { PATTERN.matchEntire(it) }.map { user to it }
            }
            .flatten()
            .mapNotNull { (user, match) ->
                val pkgName = match.groupValues[2]
                if (alreadyKnown.contains((pkgName))) {
                    return@mapNotNull null
                }

                log(TAG) { "Potentially hidden pkg: $pkgName" }

                val sourcePath = LocalPath.build(match.groupValues[1])
                log(TAG, VERBOSE) { "Reading archive $sourcePath" }
                val apkInfo = pkgOps.viewArchive(sourcePath)

                if (apkInfo == null) {
                    log(TAG, WARN) { "Failed to read APK info from $sourcePath" }
                    return@mapNotNull null
                }

                if (pkgName != apkInfo.packageName) {
                    log(TAG, ERROR) { "Packagename did not match: $apkInfo" }
                    return@mapNotNull null
                }

                HiddenPkg(
                    packageInfo = apkInfo.packageInfo,
                    userHandle = user.handle,
                )
            }
    }


    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: HiddenPkgsSource): PkgDataSource
    }

    companion object {

        private val PATTERN = Regex("^package:(.+?)=([\\w._]+)$")
        private val TAG = logTag("Pkg", "Repo", "Source", "HiddenPkgs")
    }
}