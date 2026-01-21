package eu.darken.sdmse.analyzer.ui.storage.content

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.analyzer.core.content.ContentDeleteTask
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.findContent
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.systemcleaner.core.filter.custom.EditorOptionsCreator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class ContentViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @Suppress("StaticFieldLeak") @ApplicationContext private val context: Context,
    private val analyzer: Analyzer,
    private val analyzerSettings: AnalyzerSettings,
    private val viewIntentTool: ViewIntentTool,
    private val exclusionManager: ExclusionManager,
    private val editorOptionsCreator: EditorOptionsCreator,
    private val upgradeRepo: UpgradeRepo,
    private val swiper: Swiper,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<ContentFragmentArgs>()
    private val targetStorageId = navArgs.storageId
    private val targetGroupId = navArgs.groupId
    private val targetInstallId = navArgs.installId

    val events = SingleLiveEvent<ContentItemEvents>()

    private val navigationState = MutableStateFlow<List<APath>?>(null)

    init {
        // Handle process death+restore
        analyzer.data
            .filter { it.findContentGroup() == null }
            .take(1)
            .onEach {
                log(TAG, WARN) { "Can't find $targetGroupId" }
                popNavStack()
            }
            .launchInViewModel()
    }

    private fun Analyzer.Data.findContentGroup(): ContentGroup? {
        return groups[targetGroupId]
    }

    val state = combineTransform(
        // Handle process death+restore
        analyzer.data.filter { it.findContentGroup() != null },
        analyzer.progress,
        navigationState,
        analyzerSettings.contentLayoutMode.flow,
    ) { data, progress, navLevels, layoutMode ->
        val storage = data.storages.single { it.id == targetStorageId }
        val contentGroup = data.findContentGroup()!!

        val pkgStat = targetInstallId?.let {
            data.categories[targetStorageId]
                ?.filterIsInstance<AppCategory>()?.single()
                ?.pkgStats?.get(targetInstallId)
        }

        // If the content was changed, updated our levels
        val currentLevel = contentGroup.contents.findContent { it.path == navLevels?.last() }

        val title = pkgStat?.label ?: contentGroup.label
        val subtitle = when {
            currentLevel != null -> currentLevel.label
            pkgStat?.label == null -> null
            else -> contentGroup.label
        }

        State(
            title = title,
            subtitle = subtitle,
            storage = storage,
            items = null,
            layoutMode = layoutMode,
            progress = progress,
        ).run { emit(this) }

        val items: List<ContentAdapter.Item> = (currentLevel?.children ?: contentGroup.contents)
            .sortedByDescending { it.size }
            .map { content ->
                val onItemClicked: () -> Unit = {
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
                }
                when (layoutMode) {
                    LayoutMode.LINEAR -> ContentItemListVH.Item(
                        parent = currentLevel,
                        content = content,
                        onItemClicked = onItemClicked,
                    )

                    LayoutMode.GRID -> ContentItemGridVH.Item(
                        parent = currentLevel,
                        content = content,
                        onItemClicked = onItemClicked,
                    )
                }
            }

        State(
            title = title,
            subtitle = subtitle,
            storage = storage,
            items = items,
            layoutMode = layoutMode,
            progress = progress,
        ).run { emit(this) }
    }.asLiveData2()

    fun open(lookup: APathLookup<*>) = launch {
        log(TAG) { "open(): Opening $lookup" }

        val intent = viewIntentTool.create(lookup)
        if (intent == null) {
            log(TAG, WARN) { "open(): Unable to create view intent for $lookup" }
            return@launch
        }

        log(TAG) { "open(): Launching intent for $lookup" }
        events.postValue(ContentItemEvents.OpenContent(intent))
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
        val result = analyzer.submit(task) as ContentDeleteTask.Result
        events.postValue(ContentItemEvents.ContentDeleted(result.affectedCount, result.affectedSpace))
    }

    fun onNavigateBack() {
        log(TAG) { "onNavigateBack()" }

        navigationState.value?.let { cur ->
            navigationState.value = cur.dropLast(1).takeIf { it.isNotEmpty() }
        } ?: run { popNavStack() }
    }

    fun openExclusion(item: ContentItem) = launch {
        log(TAG) { "openExclusion(${item.path})" }
        MainDirections.goToPathExclusionEditor(
            exclusionId = null,
            initial = PathExclusionEditorOptions(
                targetPath = item.path,
            ),
        ).navigate()
    }

    fun delete(items: List<ContentAdapter.Item>) {
        log(TAG) { "delete(${items.size})" }
        val targets = items
            .map {
                when (it) {
                    is ContentItemListVH.Item -> setOf(it.content)
                    is ContentItemGridVH.Item -> setOf(it.content)
                    is ContentGroupVH.Item -> it.contentGroup.contents
                    else -> throw IllegalArgumentException("Unknown type $it")
                }
            }
            .flatten()
            .toSet()
        delete(targets)
    }

    fun exclude(items: List<ContentAdapter.Item>) = launch {
        log(TAG) { "exclude(${items.size})" }
        val contentItems = items
            .map {
                when (it) {
                    is ContentItemListVH.Item -> setOf(it.content)
                    is ContentItemGridVH.Item -> setOf(it.content)
                    is ContentGroupVH.Item -> it.contentGroup.contents
                    else -> throw IllegalArgumentException("Unknown type $it")
                }
            }
            .flatten()
        val newExclusions = contentItems.map { PathExclusion(path = it.path) }.toSet()
        exclusionManager.save(newExclusions)
        val affectedContentItems = newExclusions.map { excl -> contentItems.single { it.path == excl.path } }
        events.postValue(ContentItemEvents.ExclusionsCreated(affectedContentItems))
    }

    fun createFilter(items: List<ContentAdapter.Item>) = launch {
        log(TAG) { "createFilter(${items.size})" }

        if (!upgradeRepo.isPro()) {
            log(TAG) { "Not PRO, redirecting to upgrade screen." }
            ContentFragmentDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        val targets = items
            .map {
                when (it) {
                    is ContentItemListVH.Item -> setOf(it.content)
                    is ContentItemGridVH.Item -> setOf(it.content)
                    is ContentGroupVH.Item -> it.contentGroup.contents
                    else -> throw IllegalArgumentException("Unknown type $it")
                }
            }
            .flatten()
            .mapNotNull { it.lookup }
            .toSet()

        val options = editorOptionsCreator.createOptions(targets)
        ContentFragmentDirections.goToCustomFilterEditor(initial = options).navigate()
    }

    fun createSwiperSession(items: List<ContentAdapter.Item>) = launch {
        log(TAG) { "createSwiperSession(${items.size})" }

        val paths = items
            .map {
                when (it) {
                    is ContentItemVH.Item -> setOf(it.content)
                    is ContentGroupVH.Item -> it.contentGroup.contents
                    else -> throw IllegalArgumentException("Unknown type $it")
                }
            }
            .flatten()
            .filter { !it.inaccessible }
            .map { it.path }
            .toSet()

        if (paths.isEmpty()) {
            log(TAG, WARN) { "createSwiperSession(): No accessible items to create session" }
            return@launch
        }

        val sessionId = swiper.createSession(paths)
        log(TAG) { "createSwiperSession(): Created session $sessionId with ${paths.size} paths" }
        events.postValue(ContentItemEvents.SwiperSessionCreated(sessionId, paths.size))
    }

    fun toggleLayoutMode() = launch {
        log(TAG) { "toggleLayoutMode()" }
        val newMode = when (analyzerSettings.contentLayoutMode.value()) {
            LayoutMode.LINEAR -> LayoutMode.GRID
            LayoutMode.GRID -> LayoutMode.LINEAR
        }
        analyzerSettings.contentLayoutMode.update { newMode }
    }

    data class State(
        val title: CaString?,
        val subtitle: CaString?,
        val storage: DeviceStorage,
        val items: List<ContentAdapter.Item>?,
        val layoutMode: LayoutMode,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Content", "ViewModel")
    }
}