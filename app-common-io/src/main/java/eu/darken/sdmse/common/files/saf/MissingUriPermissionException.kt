package eu.darken.sdmse.common.files.saf

import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.ReadException

class MissingUriPermissionException(
    path: APath,
    message: String = "No matching UriPermission for $path",
    cause: Throwable? = null
) : ReadException(path, message, cause), HasLocalizedError