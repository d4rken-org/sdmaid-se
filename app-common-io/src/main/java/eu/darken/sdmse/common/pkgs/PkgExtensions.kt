package eu.darken.sdmse.common.pkgs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings


fun Pkg.getSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.parse("package:${id}")
}

fun String.toPkgId() = Pkg.Id(this)