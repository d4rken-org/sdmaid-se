package eu.darken.sdmse.common.shell

import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import java.io.IOException

open class ShellOpsException(
    val cmd: ShellOpsCmd,
    message: String = "Shell error.",
    cause: Throwable? = null
) : IOException("Error executing $cmd: $message", cause)