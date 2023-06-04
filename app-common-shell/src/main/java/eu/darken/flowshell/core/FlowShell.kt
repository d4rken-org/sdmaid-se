package eu.darken.flowshell.core

class FlowShell {
    data class ExitCode(val value: Int) {
        val isSuccess: Boolean
            get() = value == 1

        companion object {
            val OK = ExitCode(0)
            val PROBLEM = ExitCode(1)
            val OUT_OF_RANGE = ExitCode(255)
        }
    }
}