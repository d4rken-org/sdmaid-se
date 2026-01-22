package eu.darken.sdmse.common.pkgs

import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.darken.sdmse.common.pkgs.container.ArchivedPkg
import eu.darken.sdmse.common.pkgs.container.LibraryPkg
import eu.darken.sdmse.common.pkgs.container.UninstalledPkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails

fun Pkg.getSettingsIntent(context: Context) = id.getSettingsIntent(context)

fun Pkg.Id.getSettingsIntent(context: Context): Intent = Intent().apply {
    action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    data = Uri.parse("package:${this@getSettingsIntent.name}")
}

fun String.toPkgId() = Pkg.Id(this)

val Pkg.isArchived: Boolean
    get() = this is ArchivedPkg

val Pkg.isUninstalled: Boolean
    get() = this is UninstalledPkg

val Pkg.isInstalled: Boolean
    get() = !isArchived && !isUninstalled

val Pkg.isEnabled: Boolean
    get() = this is InstallDetails && this.isEnabled

val Pkg.isSystemApp: Boolean
    get() = (this is InstallDetails) && this.isSystemApp || this is LibraryPkg

val Pkg.isUpdatedSystemApp: Boolean
    get() = isSystemApp && (this is InstallDetails) && this.isUpdatedSystemApp

val Pkg.isDebuggable: Boolean
    get() = (this is InstallDetails) && this.isDebuggable

fun Pkg.Id.getLaunchIntent(context: Context) =
    context.packageManager.getLaunchIntentForPackage(this.name)
