package eu.darken.sdmse.common.ipc

interface IpcHostModule {

    // Not all exception can be passed through the binder
    // See Parcel.writeException(...)
    fun Throwable.wrapToPropagate(): Exception {
        val msgBuilder = StringBuilder()
        msgBuilder.append(this.toString())
        cause?.let {
            msgBuilder.append("\nCaused by: ")
            msgBuilder.append(it.toString())
        }
        return UnsupportedOperationException(msgBuilder.toString()).also {
            it.stackTrace = this.stackTrace
            // The stacktrace is still lost and not encoded for some reason...
        }
    }
}