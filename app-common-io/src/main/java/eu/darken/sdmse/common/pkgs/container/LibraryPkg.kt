package eu.darken.sdmse.common.pkgs.container

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.SharedLibraryInfo
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.cache
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.features.PermissionDetails
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2

data class LibraryPkg(
    private val sharedLibraryInfo: SharedLibraryInfo,
    private val apkPath: APath,
    override val packageInfo: PackageInfo,
    override val userHandle: UserHandle2,
    override val installerInfo: InstallerInfo = InstallerInfo(),
) : Installed, InstallDetails, SourceAvailable, PermissionDetails {

    // Libraries that show up here are system-level by construction.
    // getPackageArchiveInfo() does not populate FLAG_SYSTEM, so the default
    // InstallDetails.isSystemApp reading applicationInfo.flags would be wrong
    // for the APK-parsed fallback path. Force it to preserve current behavior.
    override val isSystemApp: Boolean
        get() = true

    override val id: Pkg.Id
        get() = sharedLibraryInfo.toVersionedPkgId()

    @Suppress("DEPRECATION")
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

    override val icon: ((Context) -> Drawable)? = { context ->
        context.packageManager.getIcon2(id)
            ?: AppCompatResources.getDrawable(context, R.drawable.ic_baseline_local_library_24)!!
    }


    override fun toString(): String = "LibraryPkg(packageName=$packageName, path=$apkPath)"
}

/**
 * Constructs the `Pkg.Id` that the PackageManager uses to track this shared library.
 *
 * For static and SDK libraries, this is the versioned name (e.g.
 * `com.google.android.trichromelibrary_699813532`) that `dumpsys package` accepts.
 * For dynamic libraries (no version), this falls back to the raw library name.
 */
@SuppressLint("NewApi")
fun SharedLibraryInfo.toVersionedPkgId(): Pkg.Id {
    @Suppress("DEPRECATION")
    val longVersion = if (hasApiLevel(28)) longVersion else version.toLong()
    val rawId = if (longVersion == -1L) name else "${name}_$longVersion"
    return rawId.toPkgId()
}