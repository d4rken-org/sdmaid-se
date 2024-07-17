package eu.darken.sdmse.common.ipc

import java.io.IOException

class UnwrappedIPCException(
    message: String
) : IOException(message)