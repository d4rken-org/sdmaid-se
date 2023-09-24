package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed

data class AppInfo(
    val pkg: Installed,
    val isActive: Boolean?,
) {
    val label: CaString
        get() = pkg.label ?: pkg.packageName.toCaString()

    val id: Pkg.Id
        get() = pkg.id

    val installId: Installed.InstallId
        get() = pkg.installId
}
