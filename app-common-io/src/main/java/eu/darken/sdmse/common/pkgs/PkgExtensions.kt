package eu.darken.sdmse.common.pkgs

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import eu.darken.sdmse.common.pkgs.container.ArchivedPkg
import eu.darken.sdmse.common.pkgs.container.HiddenPkg
import eu.darken.sdmse.common.pkgs.container.LibraryPkg
import eu.darken.sdmse.common.pkgs.container.UninstalledPkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails

fun Pkg.getSettingsIntent(context: Context) = id.getSettingsIntent(context)

fun Pkg.Id.getSettingsIntent(context: Context): Intent = Intent().apply {
    action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    data = "package:${this@getSettingsIntent.name}".toUri()
}

fun String.toPkgId() = Pkg.Id(this)

val Pkg.isArchived: Boolean
    get() = this is ArchivedPkg

val Pkg.isUninstalled: Boolean
    get() = this is UninstalledPkg

val Pkg.isLibrary: Boolean
    get() = this is LibraryPkg

/** The union of "installed for this user but hidden" and "not installed for this user". */
val Pkg.isHidden: Boolean
    get() = this is HiddenPkg

/** Installed for this user, but the package manager hides it (a device-policy controller, `pm hide`). */
val Pkg.isHiddenInstalled: Boolean
    get() = this is HiddenPkg && isInstalledForUser

/** Known to the package manager, but not installed for this user. */
val Pkg.isNotInstalledForUser: Boolean
    get() = this is HiddenPkg && !isInstalledForUser

val Pkg.isInstalled: Boolean
    get() = !isArchived && !isUninstalled && !isNotInstalledForUser

val Pkg.isEnabled: Boolean
    get() = this is InstallDetails && this.isEnabled

val Pkg.isSystemApp: Boolean
    get() = (this is InstallDetails) && this.isSystemApp

val Pkg.isUpdatedSystemApp: Boolean
    get() = isSystemApp && (this is InstallDetails) && this.isUpdatedSystemApp

val Pkg.isDebuggable: Boolean
    get() = (this is InstallDetails) && this.isDebuggable

fun Pkg.Id.getLaunchIntent(context: Context) =
    context.packageManager.getLaunchIntentForPackage(this.name)
