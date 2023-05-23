package eu.darken.sdmse.analyzer.ui.storage.content

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ContentFragmentVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<ContentFragmentArgs>()
    private val targetStorageId = navArgs.storageId
    private val targetGroupId = navArgs.groupId
    private val targetInstallId = navArgs.installId
    private val subContentLevel = MutableStateFlow<ContentItem?>(null)

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

    val state = combine(
        analyzer.data,
        analyzer.progress,
        subContentLevel,
    ) { data, progress, contentLevel ->
        val storage = data.storages.single { it.id == targetStorageId }
        val contentGroup = data.groups[targetGroupId]

        val pkgStat = targetInstallId?.let {
            data.categories[targetStorageId]
                ?.filterIsInstance<AppCategory>()?.single()
                ?.pkgStats?.get(targetInstallId)
        }

        val title = pkgStat?.label ?: contentGroup?.label
        val subtitle = if (pkgStat?.label == null) null else contentGroup?.label

        val items = (contentLevel?.children ?: contentGroup?.contents)
            ?.sortedByDescending { it.size }
            ?.map { content ->
                ContentItemVH.Item(
                    content = content,
                    onItemClicked = {
                        if (content.size == null) {
                            log(TAG) { "No details available for $content" }
                            return@Item
                        }
                        subContentLevel.value = content
                    }
                )
            }

        State(
            title = title,
            subtitle = subtitle,
            storage = storage,
            items = items,
            progress = progress,
        )
    }.asLiveData2()

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