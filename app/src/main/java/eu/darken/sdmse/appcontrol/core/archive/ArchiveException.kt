package eu.darken.sdmse.appcontrol.core.archive

import eu.darken.sdmse.common.pkgs.features.InstallId
import okio.IOException

open class ArchiveException @JvmOverloads constructor(
    message: String? = null,
    installId: InstallId? = null,
    cause: Throwable? = null,
) : IOException(message ?: "Failed to archive $installId", cause)
