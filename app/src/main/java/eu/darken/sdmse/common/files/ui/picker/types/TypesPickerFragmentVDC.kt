package eu.darken.sdmse.common.files.ui.picker.types

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.ui.picker.PathPickerOptions
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.smart.SmartVDC
import eu.darken.sdmse.storage.core.Storage
import javax.inject.Inject

@HiltViewModel
class TypesPickerFragmentVDC @Inject constructor(
    handle: SavedStateHandle
) : SmartVDC() {

    private val options: PathPickerOptions = handle.navArgs<TypesPickerFragmentArgs>().value.options
    private val stater = DynamicStateFlow(TAG, vdcScope) {
        val types = options.allowedTypes.map {
            when (it) {
                eu.darken.sdmse.common.files.core.APath.PathType.RAW -> throw UnsupportedOperationException("$it is not supported")
                eu.darken.sdmse.common.files.core.APath.PathType.LOCAL -> Storage.Type.LOCAL
                eu.darken.sdmse.common.files.core.APath.PathType.SAF -> Storage.Type.SAF
            }
        }
        State(allowedTypes = types.sortedBy { it.name }.toList())
    }
    val state = stater.asLiveData2()

    val typeEvents = SingleLiveEvent<Storage.Type>()

    fun selectType(type: Storage.Type) {
        typeEvents.postValue(type)
    }

    data class State(
        val allowedTypes: List<Storage.Type>
    )

    companion object {
        val TAG = logTag("Picker", "Types", "VDC")
    }
}