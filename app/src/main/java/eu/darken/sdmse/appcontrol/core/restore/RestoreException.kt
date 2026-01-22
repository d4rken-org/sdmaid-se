package eu.darken.sdmse.appcontrol.core.restore

import eu.darken.sdmse.common.pkgs.features.InstallId
import okio.IOException

open class RestoreException @JvmOverloads constructor(
    message: String? = null,
    installId: InstallId? = null,
    cause: Throwable? = null,
) : IOException(message ?: "Failed to restore $installId", cause)
