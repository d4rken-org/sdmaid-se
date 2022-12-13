package eu.darken.sdmse.common.files.core.saf

import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.ReadException

class MissingUriPermissionException(
    path: APath,
    message: String = "No matching UriPermission for $path",
    cause: Throwable? = null
) : ReadException(path, message, cause), HasLocalizedError