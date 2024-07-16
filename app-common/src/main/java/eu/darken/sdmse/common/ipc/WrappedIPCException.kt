package eu.darken.sdmse.common.ipc

import java.io.IOException

class WrappedIPCException(
    override val message: String
) : IOException()