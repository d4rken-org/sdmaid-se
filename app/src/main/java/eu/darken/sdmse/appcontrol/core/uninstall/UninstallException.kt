package eu.darken.sdmse.appcontrol.core.uninstall

import eu.darken.sdmse.common.pkgs.features.Installed
import okio.IOException

open class UninstallException(
    val installId: Installed.InstallId,
    cause: Throwable? = null,
    message: String = "Failed to uninstall $installId due to $cause",
) : IOException(message, cause)