package eu.darken.sdmse.common.files.ui.picker

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.saf.SAFGateway
import eu.darken.sdmse.common.files.core.saf.SAFPath
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.smart.SmartVDC
import eu.darken.sdmse.storage.core.Storage
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PathPickerActivityVDC @Inject constructor(
    handle: SavedStateHandle,
    val safGateway: SAFGateway
) : SmartVDC() {

    private val options: PathPickerOptions = handle.navArgs<PathPickerActivityArgs>().value.options
    val launchSAFEvents = SingleLiveEvent<Intent>()
    val launchLocalEvents = SingleLiveEvent<PathPickerOptions>()
    val launchTypesEvents = SingleLiveEvent<PathPickerOptions>()

    val resultEvents = SingleLiveEvent<Pair<PathPickerResult, Boolean>>()

    init {
        // Default result is canceled
        resultEvents.postValue(PathPickerResult(options = options) to false)

        val startType = when {
            options.startPath != null -> options.startPath.pathType
            options.allowedTypes.size == 1 -> options.allowedTypes.single()
            else -> null
        }

        if (handle.get<Boolean>(KEY_CONSUMED) != true) {
            when (startType) {
                eu.darken.sdmse.common.files.core.APath.PathType.RAW -> throw UnsupportedOperationException("$startType is not supported")
                eu.darken.sdmse.common.files.core.APath.PathType.LOCAL -> showLocal()
                eu.darken.sdmse.common.files.core.APath.PathType.SAF -> showSAF()
                null -> showPicker()
            }
            handle.set(KEY_CONSUMED, true)
        }
    }

    private fun showLocal() {
        launchLocalEvents.postValue(options)
    }

    private fun showSAF() {
        launchSAFEvents.postValue(safGateway.createPickerIntent())
    }

    private fun showPicker() {
        launchTypesEvents.postValue(options)
    }

    fun onSAFPickerResult(data: Uri?) {
        Timber.tag(TAG).d("onSAFPickerResult(data=%s)", data)
        if (data != null) {
            val path = SAFPath.build(data)
            val takenPermissions = mutableSetOf<SAFPath>()
            if (!safGateway.hasPermission(path) && safGateway.takePermission(path)) {
                takenPermissions.add(path)
            }
            val result = PathPickerResult(
                options,
                selection = setOf(path),
                persistedPermissions = takenPermissions
            )
            resultEvents.postValue(result to true)
        } else {
            if (options.allowedTypes.size > 1) {
                showPicker()
            } else {
                resultEvents.postValue(PathPickerResult(options = options) to true)
            }
        }
    }

    fun onTypePicked(type: Storage.Type) {
        when (type) {
            Storage.Type.LOCAL -> showLocal()
            Storage.Type.SAF -> showSAF()
        }
    }

    fun onResult(result: PathPickerResult) {
        resultEvents.postValue(result to true)
    }

    companion object {
        val TAG = logTag("Picker", "Activity", "VDC")
        const val KEY_CONSUMED = "consumed"
    }
}