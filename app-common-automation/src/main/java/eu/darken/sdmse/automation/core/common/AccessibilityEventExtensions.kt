package eu.darken.sdmse.automation.core.common

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId

val ACSNodeInfo.pkgId: Pkg.Id get() = packageName.toString().toPkgId()