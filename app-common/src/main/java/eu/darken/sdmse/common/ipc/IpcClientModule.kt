package eu.darken.sdmse.common.ipc

import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log

interface IpcClientModule {

    fun Throwable.unwrapPropagation(): Throwable {
        val matchResult = Regex("^[a-zA-Z0-9.]+Exception").find((message ?: ""))
        val exceptionName = matchResult?.value ?: return this
        val exceptionMessage = message!!.removePrefix("$exceptionName: ").trim()

        return try {
            Class.forName(exceptionName)
                .asSubclass(Throwable::class.java)
                .getConstructor(String::class.java)
                .newInstance(exceptionMessage)
                .also { it.stackTrace = this.stackTrace }
        } catch (e: Exception) {
            log(WARN) { "Failed to unwrap exception: $this" }
            this
        }
    }

}