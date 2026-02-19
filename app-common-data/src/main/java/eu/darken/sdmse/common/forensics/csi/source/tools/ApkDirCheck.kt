package eu.darken.sdmse.common.forensics.csi.source.tools

import android.content.pm.PackageManager.*
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.source.AppSourceCheck
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

/**
 * Match an apk inside a dir
 * some_pkg/base.apk
 * some_pkg/some_pkg.apk
 */
@Reusable
class ApkDirCheck @Inject constructor(
    private val pkgOps: PkgOps,
) : AppSourceCheck {

    override suspend fun process(areaInfo: AreaInfo): AppSourceCheck.Result {
        val check1 = checkApkName(areaInfo, "${areaInfo.file.name}.apk")
        val check2 = checkApkName(areaInfo, "base.apk")
        return AppSourceCheck.Result(
            owners = check1.owners + check2.owners,
            hasKnownUnknownOwner = check1.hasKnownUnknownOwner || check2.hasKnownUnknownOwner
        )
    }

    private suspend fun checkApkName(areaInfo: AreaInfo, name: String): AppSourceCheck.Result {
        val owners = mutableSetOf<Owner>()
        var hasKnownUnknownOwner = false

        val apk = LocalPath.build(areaInfo.file as LocalPath, name)
        val baseInfo = pkgOps.viewArchive(apk, 0) ?: return AppSourceCheck.Result()

        // Normal app :')
        owners.add(Owner(baseInfo.id, areaInfo.userHandle))

        // It might be a theme/overlay...
        // https://github.com/d4rken/sdmaid-public/issues/1813

        // <overlay android:priority="1" android:targetPackage="android"/>
        val overlayTargetPkg = try {
            baseInfo.tryField<String?>("overlayTarget")?.toPkgId()
        } catch (e: Exception) {
            log(TAG) { "Checking 'overlayTarget' failed: ${e.asLog()}" }
            null
        }
        if (overlayTargetPkg != null) {
            log(TAG) { "Target via reflection, PackageInfo.overlayTarget=$overlayTargetPkg from $apk" }
            owners.add(Owner(overlayTargetPkg, areaInfo.userHandle))
        }

        val extendedInfo = pkgOps.viewArchive(apk, GET_META_DATA or GET_PERMISSIONS)

        // <meta-data android:name="target_package" android:value="android"/>
        val targetPkg = extendedInfo?.applicationInfo?.metaData?.getString("target_package")?.toPkgId()
        if (targetPkg != null) {
            log(TAG) { "Target via metadata, target_package=$targetPkg from $apk" }
            owners.add(Owner(targetPkg, areaInfo.userHandle))
        }

        // <meta-data android:name="Substratum_Target" android:value="com.whatsapp"/>
        val substratumPkg = extendedInfo?.applicationInfo?.metaData?.getString("Substratum_Target")?.toPkgId()
        if (substratumPkg != null) {
            log(TAG) { "Target via metadata, Substratum_Target=$ from $apk" }
            owners.add(Owner(substratumPkg, areaInfo.userHandle))
        }

        // <uses-permission d1p1:name="com.samsung.android.permission.SAMSUNG_OVERLAY_APPICON"/>
        hasKnownUnknownOwner = extendedInfo
            ?.requestedPermissions
            ?.any { it.contains("com.samsung.android.permission.SAMSUNG_OVERLAY_") }
            ?.also { if (it) log(TAG) { "Unknown overlay with SAMSUNG overlay permission: $apk" } }
            ?: false

        return AppSourceCheck.Result(
            owners = owners,
            hasKnownUnknownOwner = hasKnownUnknownOwner,
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ApkDirCheck): AppSourceCheck
    }

    companion object {
        val TAG: String = logTag("CSI", "App", "Tools", "ApkDirCheck")
    }
}