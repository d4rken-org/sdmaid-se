package eu.darken.sdmse.common.files.ui.picker.local

import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.App
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.WriteException
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.LocalPathLookup
import eu.darken.sdmse.common.files.core.local.toCrumbs
import eu.darken.sdmse.common.files.ui.picker.PathPickerOptions
import eu.darken.sdmse.common.files.ui.picker.PathPickerResult
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.permission.Permission
import eu.darken.sdmse.common.permission.RuntimePermissionTool
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.smart.Smart2VDC
import kotlinx.coroutines.CoroutineExceptionHandler
import javax.inject.Inject

@HiltViewModel
class LocalPickerFragmentVDC @Inject constructor(
    handle: SavedStateHandle,
    private val localGateway: LocalGateway,
    private val permissionTool: RuntimePermissionTool,
    private val dispatcherProvider: DispatcherProvider,
) : Smart2VDC(dispatcherProvider) {
    private val options: PathPickerOptions = handle.navArgs<LocalPickerFragmentArgs>().value.options
    private val fallbackPath = LocalPath.build(Environment.getExternalStorageDirectory())
    private val startPath = options.startPath as? LocalPath ?: fallbackPath

    private val mode: LocalGateway.Mode by lazy {
        options.payload.classLoader = App::class.java.classLoader
        LocalGateway.Mode.valueOf(options.payload.getString(ARG_MODE, LocalGateway.Mode.AUTO.name))
    }

    private var resourceToken: Resource<*>? = null
    private val stater = DynamicStateFlow(TAG, vdcScope) {
        State(
            currentPath = startPath,
            currentCrumbs = startPath.toCrumbs(),
            allowCreateDir = options.allowCreateDir
        )
    }
    val state = stater.asLiveData2()

    val createDirEvent = SingleLiveEvent<eu.darken.sdmse.common.files.core.APath>()
    val resultEvents = SingleLiveEvent<PathPickerResult>()
    val missingPermissionEvent = SingleLiveEvent<Permission>()
    val requestPermissionEvent = SingleLiveEvent<Permission>()

    init {
        launchErrorHandler = CoroutineExceptionHandler { _, ex ->
            log(TAG) { "Error during launch: ${ex.asLog()}" }
            errorEvents.postValue(ex)
        }

        if (!permissionTool.hasStoragePermission()) {
            missingPermissionEvent.postValue(permissionTool.getRequiredStoragePermission())
        } else {
            selectItem(startPath)
        }
    }

    override fun onCleared() {
        resourceToken?.close()
        super.onCleared()
    }

    fun selectItem(selected: eu.darken.sdmse.common.files.core.APath?) {
        val unwrapped = when (selected) {
            is LocalPath -> selected
            is LocalPathLookup -> selected.lookedUp
            else -> null
        } ?: return

        val doCd: suspend (LocalPath) -> Triple<LocalPath, List<LocalPath>, List<APathLookup<*>>> = { path ->
            val crumbs = path.toCrumbs()

            if (resourceToken == null) resourceToken = localGateway.sharedResource.get()
            val listing = localGateway.lookupFiles(path, mode = mode)
                .sortedBy { it.name.lowercase() }
                .filter { !options.onlyDirs || it.isDirectory }

            Triple(path, crumbs, listing)
        }

        launch {
            val (path, crumbs, listing) = doCd(unwrapped)

            stater.updateBlocking {
                copy(
                    currentPath = path,
                    currentListing = listing,
                    currentCrumbs = crumbs
                )
            }
        }
    }

    fun createDirRequest() {
        launch {
            val current = stater.value().currentPath
            createDirEvent.postValue(current)
        }
    }

    fun createDir(name: String) = launch {
        val current = stater.value().currentPath

        val child = current.child(name) as LocalPath
        if (localGateway.createDir(child, mode = mode)) {
            selectItem(child)
        } else {
            throw WriteException(child)
        }
    }

    fun finishSelection() = launch {
        val selected = setOf(stater.value().currentPath)
        val result = PathPickerResult(
            options = options,
            selection = selected
        )
        resultEvents.postValue(result)
    }

    fun goHome() {
        selectItem(startPath)
    }

    fun onUpdatePermission(permission: Permission) {
        if (!permissionTool.hasPermission(permission)) return

        selectItem(options.startPath)
    }

    fun grantPermission(permission: Permission) {
        requestPermissionEvent.postValue(permission)
    }

    suspend fun canCreateFolder(currentText: String): Boolean {
        if (currentText.isEmpty()) return false
        return stater.value().currentListing?.none { it.name == currentText } ?: false
    }

    data class State(
        val currentPath: eu.darken.sdmse.common.files.core.APath,
        val currentCrumbs: List<eu.darken.sdmse.common.files.core.APath>,
        val currentListing: List<APathLookup<*>>? = null,
        val allowCreateDir: Boolean = false
    )

    companion object {
        val TAG = logTag("Picker", "Local", "VDC")
        const val ARG_MODE = "picker.local.mode"
    }
}