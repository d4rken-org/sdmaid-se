package eu.darken.sdmse.common.pkgs.container

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.SharedLibraryInfo
import androidx.appcompat.content.res.AppCompatResources
import eu.darken.sdmse.common.ca.*
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.ReadableApk
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2

data class LibraryPkg(
    private val sharedLibraryInfo: SharedLibraryInfo,
    private val apkPath: APath,
    override val packageInfo: PackageInfo,
    override val userHandle: UserHandle2,
) : Installed, ReadableApk {

    override val id: Pkg.Id
        get() {
            val rawId = if (versionCode == -1L) {
                sharedLibraryInfo.name
            } else {
                "${sharedLibraryInfo.name}_${versionCode}"
            }
            return rawId.toPkgId()
        }

    @get:SuppressLint("NewApi")
    override val versionCode: Long
        get() = if (hasApiLevel(28)) {
            sharedLibraryInfo.longVersion
        } else {
            sharedLibraryInfo.version.toLong()
        }

    override val sourceDir: APath
        get() = apkPath

    override val label: CaString = caString { context ->
        context.packageManager.getLabel2(id)
            ?: sharedLibraryInfo.name
            ?: id.name
    }.cache()


    override fun <T> tryField(fieldName: String): T? {
        val field = SharedLibraryInfo::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        return field.get(sharedLibraryInfo) as? T
    }

    override val icon: CaDrawable = caDrawable { context ->
        context.packageManager.getIcon2(id)
            ?: AppCompatResources.getDrawable(context, R.drawable.ic_baseline_local_library_24)!!
    }.cache()


    override fun toString(): String = "LibraryPkg(packageName=$packageName, path=$apkPath)"
}