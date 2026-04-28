package eu.darken.sdmse.analyzer.ui.storage.content

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.analyzer.core.content.ContentDeleteTask
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.analyzer.core.storage.findContent
import eu.darken.sdmse.analyzer.ui.ContentRoute
import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.FilterEditorOptionsCreator
import eu.darken.sdmse.common.files.SwiperSessionCreator
import eu.darken.sdmse.common.filter.CustomFilterEditorOptions
import eu.darken.sdmse.common.filter.CustomFilterEditorRoute
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.ui.PathExclusionEditorRoute
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class ContentViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val analyzer: Analyzer,
    private val analyzerSettings: AnalyzerSettings,
    private val viewIntentTool: ViewIntentTool,
    private val exclusionManager: ExclusionManager,
    private val filterEditorOptionsCreator: FilterEditorOptionsCreator,
    private val upgradeRepo: UpgradeRepo,
    private val swiperSessionCreator: SwiperSessionCreator,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val routeFlow = MutableStateFlow<ContentRoute?>(null)
    private val navigationState = MutableStateFlow<List<APath>?>(null)

    val events = SingleEventFlow<Event>()

    fun bindRoute(route: ContentRoute) {
        if (routeFlow.value != null) return
        log(TAG, INFO) { "bindRoute(${route.groupId}, installId=${route.installId})" }
        routeFlow.value = route
    }

    init {
        // Process-death handler.
        routeFlow
            .filterNotNull()
            .flatMapLatest { route ->
                analyzer.data
                    .filter { it.findContentGroup(route) == null }
                    .take(1)
                    .onEach {
                        log(TAG, WARN) { "Can't find ${route.groupId}" }
                        navUp()
                    }
            }
            .launchInViewModel()
    }

    private fun Analyzer.Data.findContentGroup(route: ContentRoute): ContentGroup? = groups[route.groupId]

    private fun Analyzer.Data.isSystemGroup(route: ContentRoute): Boolean {
        return categories[route.storageId]
            ?.filterIsInstance<SystemCategory>()
            ?.any { category -> category.groups.any { it.id == route.groupId } }
            ?: false
    }

    val state: StateFlow<State> = routeFlow
        .filterNotNull()
        .flatMapLatest { route ->
            combineTransform(
                analyzer.data,
                analyzer.progress,
                navigationState,
                analyzerSettings.contentLayoutMode.flow,
            ) { data, progress, navLevels, layoutMode ->
                val storage = data.storages.firstOrNull { it.id == route.storageId }
                val contentGroup = data.findContentGroup(route)
                if (storage == null || contentGroup == null) {
                    emit(State.NotFound)
                    return@combineTransform
                }
                val isReadOnly = data.isSystemGroup(route)
                val pkgStat = route.installId?.let { installId ->
                    data.categories[route.storageId]
                        ?.filterIsInstance<AppCategory>()?.singleOrNull()
                        ?.pkgStats?.get(installId)
                }
                val currentLevel = contentGroup.contents.findContent { it.path == navLevels?.last() }
                val title = pkgStat?.label ?: contentGroup.label
                val subtitle = when {
                    currentLevel != null -> currentLevel.label
                    pkgStat?.label == null -> null
                    else -> contentGroup.label
                }

                // Loading frame: show progress overlay, no items yet.
                emit(
                    State.Ready(
                        title = title,
                        subtitle = subtitle,
                        storage = storage,
                        items = null,
                        layoutMode = layoutMode,
                        progress = progress,
                        isReadOnly = isReadOnly,
                        showSystemInfoBanner = isReadOnly && currentLevel == null,
                    ),
                )

                val siblings = currentLevel?.children ?: contentGroup.contents
                val maxSiblingSize = siblings.mapNotNull { it.size }.maxOrNull()
                val sortedItems = siblings
                    .sortedByDescending { it.size }
                    .map { content ->
                        Item(
                            parent = currentLevel,
                            content = content,
                            sizeRatio = computeSizeRatio(content.size, maxSiblingSize),
                        )
                    }

                emit(
                    State.Ready(
                        title = title,
                        subtitle = subtitle,
                        storage = storage,
                        items = sortedItems,
                        layoutMode = layoutMode,
                        progress = progress,
                        isReadOnly = isReadOnly,
                        showSystemInfoBanner = isReadOnly && currentLevel == null,
                    ),
                )
            }
        }
        .safeStateIn(initialValue = State.Loading, onError = { State.NotFound })

    fun onItemClick(item: Item) = launch {
        val content = item.content
        when (content.type) {
            FileType.FILE -> {
                val lookup = content.lookup
                if (lookup != null) {
                    open(lookup)
                } else {
                    log(TAG) { "Content has no lookup, can't open: $content" }
                }
            }
            else -> if (content.inaccessible) {
                log(TAG) { "No details available for $content" }
                events.emit(Event.ShowNoAccessHint(content))
            } else {
                navigationState.value = (navigationState.value ?: emptyList()).plus(content.path)
            }
        }
    }

    private fun open(lookup: APathLookup<*>) = launch {
        log(TAG) { "open(): Opening $lookup" }
        val intent = viewIntentTool.create(lookup)
        if (intent == null) {
            log(TAG, WARN) { "open(): Unable to create view intent for $lookup" }
            return@launch
        }
        events.emit(Event.OpenContent(intent))
    }

    fun onNavigateBack() {
        log(TAG) { "onNavigateBack()" }
        navigationState.value?.let { cur ->
            navigationState.value = cur.dropLast(1).takeIf { it.isNotEmpty() }
        } ?: run { navUp() }
    }

    fun onDeleteSelected(items: Set<ContentItem>) = launch {
        val route = routeFlow.value ?: return@launch
        log(TAG) { "onDeleteSelected(): ${items.size}" }
        val data = analyzer.data.first()
        if (data.isSystemGroup(route)) {
            log(TAG, WARN) { "delete(): Blocked — system content is read-only" }
            return@launch
        }
        val targets = items.map { it.path }.toSet()
        if (targets.isEmpty()) return@launch
        val task = ContentDeleteTask(
            storageId = route.storageId,
            groupId = route.groupId,
            targetPkg = route.installId,
            targets = targets,
        )
        val result = analyzer.submit(task) as ContentDeleteTask.Result
        events.emit(Event.ContentDeleted(result.affectedCount, result.affectedSpace))
    }

    fun onExcludeSelected(items: Set<ContentItem>) = launch {
        log(TAG) { "onExcludeSelected(): ${items.size}" }
        val newExclusions = items.map { PathExclusion(path = it.path) }.toSet()
        if (newExclusions.isEmpty()) return@launch
        exclusionManager.save(newExclusions)
        val affected = newExclusions.map { excl -> items.single { it.path == excl.path } }
        events.emit(Event.ExclusionsCreated(affected))
    }

    fun onCreateFilter(items: Set<ContentItem>) = launch {
        val route = routeFlow.value ?: return@launch
        log(TAG) { "onCreateFilter(): ${items.size}" }
        if (analyzer.data.first().isSystemGroup(route)) {
            log(TAG, WARN) { "createFilter(): Blocked — system content is read-only" }
            return@launch
        }
        if (!upgradeRepo.isPro()) {
            log(TAG) { "Not PRO, redirecting to upgrade screen." }
            navTo(UpgradeRoute())
            return@launch
        }
        val targets = items.mapNotNull { it.lookup }.toSet()
        if (targets.isEmpty()) return@launch
        val options = filterEditorOptionsCreator.createOptions(targets)
        navTo(CustomFilterEditorRoute(initial = options as CustomFilterEditorOptions))
    }

    fun onCreateSwiperSession(items: Set<ContentItem>) = launch {
        val route = routeFlow.value ?: return@launch
        log(TAG) { "onCreateSwiperSession(): ${items.size}" }
        if (analyzer.data.first().isSystemGroup(route)) {
            log(TAG, WARN) { "createSwiperSession(): Blocked — system content is read-only" }
            return@launch
        }
        val paths = items
            .filter { !it.inaccessible }
            .map { it.path }
            .toSet()
        if (paths.isEmpty()) {
            log(TAG, WARN) { "createSwiperSession(): No accessible items to create session" }
            return@launch
        }
        val sessionId = swiperSessionCreator.createSession(paths)
        log(TAG) { "createSwiperSession(): Created session $sessionId with ${paths.size} paths" }
        events.emit(Event.SwiperSessionCreated(sessionId, paths.size))
    }

    fun onOpenExclusion(item: ContentItem) {
        log(TAG) { "openExclusion(${item.path})" }
        navTo(
            PathExclusionEditorRoute(
                initial = PathExclusionEditorOptions(targetPath = item.path),
            ),
        )
    }

    fun onLayoutModeToggle() = launch {
        log(TAG) { "onLayoutModeToggle()" }
        when (analyzerSettings.contentLayoutMode.value()) {
            LayoutMode.LINEAR -> analyzerSettings.contentLayoutMode.value(LayoutMode.GRID)
            LayoutMode.GRID -> analyzerSettings.contentLayoutMode.value(LayoutMode.LINEAR)
        }
    }

    private fun computeSizeRatio(size: Long?, maxSiblingSize: Long?): Float? {
        if (size == null || maxSiblingSize == null || maxSiblingSize <= 0L) return null
        return (size.toFloat() / maxSiblingSize.toFloat()).coerceIn(0f, 1f)
    }

    data class Item(
        val parent: ContentItem?,
        val content: ContentItem,
        val sizeRatio: Float?,
    )

    sealed interface State {
        data object Loading : State
        data class Ready(
            val title: CaString?,
            val subtitle: CaString?,
            val storage: DeviceStorage,
            val items: List<Item>?,
            val layoutMode: LayoutMode,
            val progress: Progress.Data?,
            val isReadOnly: Boolean,
            val showSystemInfoBanner: Boolean,
        ) : State
        data object NotFound : State
    }

    sealed interface Event {
        data class ShowNoAccessHint(val item: ContentItem) : Event
        data class ExclusionsCreated(val items: List<ContentItem>) : Event
        data class ContentDeleted(val count: Int, val freedSpace: Long) : Event
        data class OpenContent(val intent: Intent) : Event
        data class SwiperSessionCreated(val sessionId: String, val itemCount: Int) : Event
    }

    companion object {
        private val TAG = logTag("Analyzer", "Content", "ViewModel")
    }
}
