package eu.darken.sdmse.common.ipc

import java.io.IOException

class UnwrappedIPCException(
    override val message: String
) : IOException()