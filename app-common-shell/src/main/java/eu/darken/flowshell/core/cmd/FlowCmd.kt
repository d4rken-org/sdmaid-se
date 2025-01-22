package eu.darken.flowshell.core.cmd

import eu.darken.flowshell.core.process.FlowProcess

data class FlowCmd(
    val instructions: List<String>,
) {

    constructor(vararg instrs: String) : this(instrs.toList())

    data class Result(
        val original: FlowCmd,
        val exitCode: FlowProcess.ExitCode,
        val output: List<String>,
        val errors: List<String>,
    ) {
        val isSuccessful: Boolean
            get() = exitCode == FlowProcess.ExitCode.OK

        val merged: List<String>
            get() = output + errors
    }
}