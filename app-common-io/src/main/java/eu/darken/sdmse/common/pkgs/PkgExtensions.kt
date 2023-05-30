package eu.darken.sdmse.common.pkgs

import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.darken.sdmse.common.pkgs.features.ExtendedInstallData

fun Pkg.getSettingsIntent(context: Context) = id.getSettingsIntent(context)

fun Pkg.Id.getSettingsIntent(context: Context): Intent = Intent().apply {
    action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    data = Uri.parse("package:${this@getSettingsIntent.name}")
}

fun String.toPkgId() = Pkg.Id(this)

val Pkg.isEnabled: Boolean
    get() = this is ExtendedInstallData && this.isEnabled

val Pkg.isSystemApp: Boolean
    get() = (this !is ExtendedInstallData) || this.isSystemApp

val Pkg.isUpdatedSystemApp: Boolean
    get() = (this !is ExtendedInstallData) || this.isUpdatedSystemApp