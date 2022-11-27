package eu.darken.sdmse.common.files.ui.picker

import androidx.lifecycle.ViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.storage.core.Storage
import timber.log.Timber

class SharedPathPickerVM
    : ViewModel() {

    val resultEvent = SingleLiveEvent<PathPickerResult>()
    val typeEvent = SingleLiveEvent<Storage.Type>()

    init {
        Timber.tag(TAG).d("Init: %s", this)
    }

    fun postResult(result: PathPickerResult) {
        resultEvent.postValue(result)
    }

    fun launchType(type: Storage.Type) {
        typeEvent.postValue(type)
    }

    companion object {
        private val TAG = logTag("Picker", "SharedPickerVM")
    }
}