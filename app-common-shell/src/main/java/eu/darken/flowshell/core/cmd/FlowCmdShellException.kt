package eu.darken.flowshell.core.cmd

import eu.darken.flowshell.core.FlowShellException

open class FlowCmdShellException(
    message: String? = null,
    cause: Throwable? = null,
) : FlowShellException(message, cause)