package eu.darken.flowshell.core.cmd

import eu.darken.flowshell.core.FlowShell

data class FlowCmd(
    val instructions: List<String>,
) {

    constructor(vararg instrs: String) : this(instrs.toList())

    data class Result(
        val flowCmd: FlowCmd,
        val exitCode: FlowShell.ExitCode,
        val output: List<String>?,
        val errors: List<String>?,
    ) {
        val isSuccessful: Boolean
            get() = exitCode == FlowShell.ExitCode.OK
    }
}