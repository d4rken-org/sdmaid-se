package eu.darken.flowshell.core

import java.io.IOException

open class FlowShellException(
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)