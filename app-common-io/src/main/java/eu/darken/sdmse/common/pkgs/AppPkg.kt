package eu.darken.sdmse.common.pkgs

import android.content.pm.PackageInfo

class AppPkg(packageInfo: PackageInfo) : eu.darken.sdmse.common.pkgs.BasePkgInfo(packageInfo) {

    override val installLocation: Int = packageInfo.installLocation

    override val firstInstallTime: Long = packageInfo.firstInstallTime

    override val lastUpdateTime: Long = packageInfo.lastUpdateTime

    override val sourceDir: String?
        get() {
            if (applicationInfo == null) return null
            return if (applicationInfo!!.sourceDir.isEmpty()) null else applicationInfo!!.sourceDir
        }

    override val packageType: eu.darken.sdmse.common.pkgs.Pkg.Type =
        _root_ide_package_.eu.darken.sdmse.common.pkgs.Pkg.Type.NORMAL

    @Throws(Exception::class)
    override fun <T> tryField(fieldName: String): T? {
        val field = PackageInfo::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(packageInfo) as? T
    }
}
