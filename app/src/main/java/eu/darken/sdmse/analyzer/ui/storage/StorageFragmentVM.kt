package eu.darken.sdmse.analyzer.ui.storage

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.AnalyzerScanTask
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.appcontrol.ui.list.AppControlListRowVH
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class StorageFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    init {
        analyzer.data
            .take(1)
            .filter { it == null }
            .onEach { analyzer.submit(AnalyzerScanTask()) }
            .launchInViewModel()
    }


    val state = combine(
        analyzer.data,
        analyzer.progress,
    ) { data, progress ->

        State(
            storages = emptyList(),
            progress = progress,
        )
    }.asLiveData2()

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        analyzer.submit(AnalyzerScanTask())
    }

    data class State(
        val storages: List<AppControlListRowVH.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Fragment", "VM")
    }
}