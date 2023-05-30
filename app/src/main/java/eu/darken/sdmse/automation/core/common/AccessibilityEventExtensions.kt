package eu.darken.sdmse.automation.core.common

import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId

val AccessibilityNodeInfo.pkgId: Pkg.Id get() = packageName.toString().toPkgId()