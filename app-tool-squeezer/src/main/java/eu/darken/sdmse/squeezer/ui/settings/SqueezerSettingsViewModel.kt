package eu.darken.sdmse.squeezer.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject


@HiltViewModel
class SqueezerSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: SqueezerSettings,
    private val historyDatabase: CompressionHistoryDatabase,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val state: StateFlow<State> = combine(
        settings.includeJpeg.flow,
        settings.includeWebp.flow,
        settings.includeVideo.flow,
        settings.skipPreviouslyCompressed.flow,
        settings.writeExifMarker.flow,
        historyDatabase.count,
        historyDatabase.databaseSize,
    ) { jpeg, webp, video, skipCompressed, exif, historyCount, historySize ->
        State(
            includeJpeg = jpeg,
            includeWebp = webp,
            includeVideo = video,
            skipPreviouslyCompressed = skipCompressed,
            writeExifMarker = exif,
            historyCount = historyCount,
            historyDatabaseSize = historySize,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun setIncludeJpeg(value: Boolean) = launch {
        settings.includeJpeg.value(value)
    }

    fun setIncludeWebp(value: Boolean) = launch {
        settings.includeWebp.value(value)
    }

    fun setIncludeVideo(value: Boolean) = launch {
        settings.includeVideo.value(value)
    }

    fun setSkipPreviouslyCompressed(value: Boolean) = launch {
        settings.skipPreviouslyCompressed.value(value)
    }

    fun setWriteExifMarker(value: Boolean) = launch {
        settings.writeExifMarker.value(value)
    }

    fun clearHistory() = launch {
        log(TAG) { "clearHistory()" }
        historyDatabase.clear()
    }

    data class State(
        val includeJpeg: Boolean = true,
        val includeWebp: Boolean = true,
        val includeVideo: Boolean = false,
        val skipPreviouslyCompressed: Boolean = true,
        val writeExifMarker: Boolean = false,
        val historyCount: Int = 0,
        val historyDatabaseSize: Long = 0L,
    )

    companion object {
        private val TAG = logTag("Settings", "Squeezer", "ViewModel")
    }
}
