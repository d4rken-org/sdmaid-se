package eu.darken.sdmse.common.pkgs.pkgops

import java.io.IOException

open class PkgOpsException @JvmOverloads constructor(
    message: String? = null,
    cause: Throwable? = null
) : IOException(message, cause)