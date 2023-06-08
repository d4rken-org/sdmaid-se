package eu.darken.sdmse.common.debug.recorder.core

import eu.darken.sdmse.common.debug.logging.FileLogger
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

class Recorder @Inject constructor() {
    private val mutex = Mutex()
    private var fileLogger: FileLogger? = null

    val isRecording: Boolean
        get() = path != null

    var path: File? = null
        private set

    suspend fun start(path: File) = mutex.withLock {
        if (fileLogger != null) return@withLock
        this.path = path
        fileLogger = FileLogger(path)
        fileLogger?.let {
            it.start()
            Logging.install(it)
            log(TAG, INFO) { "Now logging to file!" }
        }
    }

    suspend fun stop() = mutex.withLock {
        fileLogger?.let {
            log(TAG, INFO) { "Stopping file-logger-tree: $it" }
            Logging.remove(it)
            it.stop()
            fileLogger = null
            this.path = null
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "eu.darken.sdmse.common.debug.recording.core.Recorder")
    }

}