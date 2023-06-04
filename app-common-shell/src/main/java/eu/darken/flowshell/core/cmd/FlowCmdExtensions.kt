package eu.darken.flowshell.core.cmd

suspend fun FlowCmd.execute() = FlowCmdShell().submit(this)

val FlowCmd.Result.exception: Throwable?
    get() = if (isSuccessful) null else FlowCmdShellException(
        message = "Command failed: exitCode=$exitCode:${errors?.joinToString("\n")}",
    )