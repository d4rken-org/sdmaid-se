package eu.darken.flowshell.core.cmd

import eu.darken.flowshell.core.FlowShell
import eu.darken.flowshell.core.FlowShellDebug
import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log

class FlowCmdShell {

    suspend fun submit(flowCmd: FlowCmd): FlowCmd.Result {
        log(TAG, VERBOSE) { "submit(): $flowCmd..." }
        val rxCmd = eu.darken.rxshell.cmd.Cmd.builder(
            flowCmd.instructions
        )
        val rxResult = rxCmd.execute(RxCmdShell.builder().build())
        val result: FlowCmd.Result = FlowCmd.Result(
            flowCmd = flowCmd,
            exitCode = FlowShell.ExitCode(rxResult.exitCode),
            output = rxResult.output,
            errors = rxResult.errors
        )
        log(TAG) { "submit(): $flowCmd -> $result" }
        return result
    }


    companion object {
        private val TAG = FlowShellDebug.logTag("FlowCmdShell")
    }
}