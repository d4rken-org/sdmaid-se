package eu.darken.sdmse.appcontrol.core.uninstall

import eu.darken.sdmse.common.pkgs.features.Installed
import okio.IOException

open class UninstallException @JvmOverloads constructor(
    message: String? = null,
    installId: Installed.InstallId? = null,
    cause: Throwable? = null,
) : IOException(message ?: "Failed to uninstall $installId", cause)