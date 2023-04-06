package eu.darken.sdmse.common.pkgs.sources

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.rxshell.cmd.Cmd
import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgDataSource
import eu.darken.sdmse.common.pkgs.container.HiddenPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ShellPkgSource @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val pkgOps: PkgOps,
) : PkgDataSource {


    override suspend fun getPkgs(): Collection<Installed> = pkgOps.useRes {
        log(TAG) { "getPkgs()" }

        val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway
        if (!localGateway.hasRoot()) return@useRes emptySet()
        // TODO run this via root service?
        val result = Cmd.builder("pm list packages -f").execute(RxCmdShell.builder().root(true).build())
        Timber.tag(TAG).d("Result: %s", result)

        result.output
            .map { PATTERN.matcher(it) }
            .filter { it.matches() }
            .mapNotNull { match ->
                val pkgName = match.group(2)

                val sourcePath = LocalPath.build(match.group(1))
                val apkInfo = pkgOps.viewArchive(sourcePath)

                if (pkgName != apkInfo?.packageName) {
                    log(TAG, ERROR) { "Packagename did not match: $apkInfo" }
                    return@mapNotNull null
                }

                apkInfo?.let {
                    HiddenPkg(
                        packageInfo = apkInfo.packageInfo,
                    )
                }
            }
    }


    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ShellPkgSource): PkgDataSource
    }

    companion object {

        private val PATTERN = Pattern.compile("^(?:package:)(.+?)(?:=)([\\w._]+)$")
        private val TAG = logTag("PkgRepo", "Source", "Shell")
    }
}