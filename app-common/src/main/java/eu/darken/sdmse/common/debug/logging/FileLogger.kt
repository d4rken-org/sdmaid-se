package eu.darken.sdmse.common.debug.logging

import android.util.Log
import eu.darken.sdmse.common.debug.Bugs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.time.Instant


class FileLogger(private val logFile: File) : Logging.Logger {
    private var logWriter: OutputStreamWriter? = null

    @Suppress("SetWorldWritable", "SetWorldReadable")
    @Synchronized
    fun start() {
        if (logWriter != null) return
        Log.i(TAG, "Starting logger for " + logFile.path)
        try {
            val parentDir = logFile.parentFile!!
            if (parentDir.isFile) {
                // TODO Remove in a future version
                Log.w(TAG, "Pre v1.4.8 logging, active, deleting $parentDir")
                parentDir.delete()
            }
            logFile.parentFile!!.mkdirs()
            if (logFile.createNewFile()) Log.i(TAG, "File logger writing to ${logFile.path}")
            if (logFile.setReadable(true, false)) Log.i(TAG, "Debug run log read permission set")
            if (logFile.setWritable(true, false)) Log.i(TAG, "Debug run log write permission set")
        } catch (e: IOException) {
            Log.e(TAG, "Log writer failed to init log file", e)
            e.printStackTrace()
        }

        try {
            logWriter = OutputStreamWriter(FileOutputStream(logFile, true))
            logWriter!!.write("=== BEGIN ${Bugs.processTag} ===\n")
            logWriter!!.write("Logfile: $logFile\n")
            logWriter!!.flush()
            Log.i(TAG, "File logger started.")
        } catch (e: IOException) {
            Log.e(TAG, "Log writer failed to start", e)
            e.printStackTrace()

            logFile.delete()
            if (logWriter != null) logWriter!!.close()
        }

    }

    @Synchronized
    fun stop() {
        logWriter?.let {
            logWriter = null
            try {
                it.write("=== END ===\n")
                it.close()
            } catch (ignore: IOException) {
            }
            Log.i(TAG, "File logger stopped.")
        }
    }

    override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
        logWriter?.let {
            try {
                it.write("${Instant.ofEpochMilli(System.currentTimeMillis())}  ${priority.shortLabel}/$tag: $message\n")
                it.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write log line.", e)
                try {
                    it.close()
                } catch (ignore: Exception) {
                }
                logWriter = null
            }
        }
    }

    override fun toString(): String = "FileLogger(file=$logFile)"

    companion object {
        private val TAG = logTag("Debug", "FileLogger")
    }
}

