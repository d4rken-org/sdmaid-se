package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.common.pkgs.features.Installed

data class AppJunk(
    val pkg: Installed,
) {
    val size: Long = TODO()
}
