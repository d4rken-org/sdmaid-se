package eu.darken.sdmse.common.files.saf

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.ReadException

class MissingUriPermissionException(
    message: String? = "No matching UriPermission",
    path: APath? = null,
    cause: Throwable? = null,
) : ReadException(message = message, cause = cause, path = path)