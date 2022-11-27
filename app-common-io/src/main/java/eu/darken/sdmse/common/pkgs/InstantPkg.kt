package eu.darken.sdmse.common.pkgs

import android.content.pm.PackageInfo

class InstantPkg(packageInfo: PackageInfo, override val sourceDir: String) : BasePkgInfo(packageInfo) {

    override var lastUpdateTime: Long = 0

    override var firstInstallTime: Long = 0

    override val installLocation: Int = 0

    override val packageType: Pkg.Type = Pkg.Type.INSTANT
}
