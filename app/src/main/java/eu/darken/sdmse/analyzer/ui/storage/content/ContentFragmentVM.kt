package eu.darken.sdmse.analyzer.ui.storage.content

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.content.ContentDeleteTask
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.findContent
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.File
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.exists
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ContentFragmentVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @Suppress("StaticFieldLeak") @ApplicationContext private val context: Context,
    private val analyzer: Analyzer,
    private val mimeTypeTool: MimeTypeTool,
    private val exclusionManager: ExclusionManager,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<ContentFragmentArgs>()
    private val targetStorageId = navArgs.storageId
    private val targetGroupId = navArgs.groupId
    private val targetInstallId = navArgs.installId

    val events = SingleLiveEvent<ContentItemEvents>()

    private val navigationState = MutableStateFlow<List<APath>?>(null)

    init {
        analyzer.data
            .filter { it.groups[targetGroupId] == null }
            .take(1)
            .onEach {
                log(TAG, WARN) { "Can't find $targetGroupId" }
                popNavStack()
            }
            .map { }
            .launchInViewModel()
    }

    val state = combineTransform(
        analyzer.data,
        analyzer.progress,
        navigationState,
    ) { data, progress, navLevels ->
        val storage = data.storages.single { it.id == targetStorageId }
        val contentGroup = data.groups[targetGroupId]

        val pkgStat = targetInstallId?.let {
            data.categories[targetStorageId]
                ?.filterIsInstance<AppCategory>()?.single()
                ?.pkgStats?.get(targetInstallId)
        }

        // If the content was changed, updated our levels
        val currentLevel = contentGroup?.contents
            ?.findContent { it.path == navLevels?.last() }

        val title = pkgStat?.label ?: contentGroup?.label
        val subtitle = when {
            currentLevel != null -> currentLevel.label
            pkgStat?.label == null -> null
            else -> contentGroup?.label
        }

        State(
            title = title,
            subtitle = subtitle,
            storage = storage,
            items = null,
            progress = progress,
        ).run { emit(this) }

        val items = (currentLevel?.children ?: contentGroup?.contents)
            ?.sortedByDescending { it.size }
            ?.map { content ->
                ContentItemVH.Item(
                    parent = currentLevel,
                    content = content,
                    onItemClicked = {
                        when (content.type) {
                            FileType.FILE -> if (content.lookup != null) {
                                open(content.lookup)
                            } else {
                                log(TAG) { "Content has no lookup, can't open: $content" }
                            }

                            else -> if (content.inaccessible) {
                                log(TAG) { "No details available for $content" }
                                events.postValue(ContentItemEvents.ShowNoAccessHint(content))
                            } else {
                                navigationState.value = (navigationState.value ?: emptyList()).plus(content.path)
                            }
                        }
                    },
                    onItemLongPressed = {
                        launch {
                            val exclusionExists = exclusionManager.exists(PathExclusion.createId(content.path))
                            events.postValue(ContentItemEvents.ContentLongPressActions(content, exclusionExists))
                        }
                    }
                )
            }

        State(
            title = title,
            subtitle = subtitle,
            storage = storage,
            items = items,
            progress = progress,
        ).run { emit(this) }
    }.asLiveData2()

    fun open(lookup: APathLookup<*>) = launch {
        log(TAG) { "open($lookup)" }

        if (lookup !is LocalPathLookup) {
            log(TAG) { "Can't open unsupported path type: ${lookup.pathType}" }
            return@launch
        }
        val javaPath = File(lookup.path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", javaPath)
        val mimeType = mimeTypeTool.determineMimeType(lookup)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            setDataAndType(uri, mimeType)
        }

        val chooserIntent = Intent.createChooser(intent, lookup.name).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
    }

    fun delete(items: Set<ContentItem>) = launch {
        log(TAG) { "delete(): $items" }
        val targets = items.map { it.path }.toSet()
        val task = ContentDeleteTask(
            storageId = targetStorageId,
            groupId = targetGroupId,
            targetPkg = targetInstallId,
            targets = targets
        )
        analyzer.submit(task)
    }

    fun onNavigateBack() {
        log(TAG) { "onNavigateBack()" }

        navigationState.value?.let { cur ->
            navigationState.value = cur.dropLast(1).takeIf { it.isNotEmpty() }
        } ?: run { popNavStack() }
    }

    fun openExclusion(item: ContentItem) = launch {
        log(TAG) { "createExclusion(${item.path})" }
        MainDirections.goToPathExclusionEditor(
            exclusionId = null,
            initial = PathExclusionEditorOptions(
                targetPath = item.path,
            ),
        ).navigate()
    }

    data class State(
        val title: CaString?,
        val subtitle: CaString?,
        val storage: DeviceStorage,
        val items: List<ContentItemVH.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Content", "Fragment", "VM")
    }
}