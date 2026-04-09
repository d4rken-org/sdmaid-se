package eu.darken.sdmse.analyzer.ui.storage.apps

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.AppDetailsRoute
import eu.darken.sdmse.analyzer.ui.AppsRoute
import eu.darken.sdmse.analyzer.ui.storage.computeSizeBarRatio
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class AppsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    private val targetStorageId: StorageId = AppsRoute.from(handle).storageId

    private val searchQuery = MutableStateFlow("")

    private var lastCategory: AppCategory? = null
    private val queryCacheLabel = mutableMapOf<Pkg.Id, String>()
    private val queryCachePkg = mutableMapOf<Pkg.Id, String>()

    init {
        // Handle process death+restore
        analyzer.data
            .filter { it.findAppCategory() == null }
            .take(1)
            .onEach {
                log(TAG, WARN) { "Can't find app category for $targetStorageId" }
                popNavStack()
            }
            .launchInViewModel()
    }

    private fun Analyzer.Data.findAppCategory(): AppCategory? {
        return categories[targetStorageId]?.filterIsInstance<AppCategory>()?.singleOrNull()
    }

    val state = combine(
        // Handle process death+restore
        analyzer.data.filter { it.findAppCategory() != null },
        analyzer.progress,
        searchQuery,
    ) { data, progress, query ->
        val storage = data.storages.single { it.id == targetStorageId }
        val category = data.findAppCategory()!!
        val queryNormalized = query.lowercase().trim()

        if (category !== lastCategory) {
            queryCacheLabel.clear()
            queryCachePkg.clear()
            lastCategory = category
        }

        val filteredPkgStats = category.pkgStats
            .filter { (_, pkgStat) ->
                if (queryNormalized.isEmpty()) return@filter true

                val normalizedPkg = queryCachePkg.getOrPut(pkgStat.pkg.id) {
                    pkgStat.pkg.packageName.lowercase()
                }
                if (normalizedPkg.contains(queryNormalized)) return@filter true

                val normalizedLabel = queryCacheLabel.getOrPut(pkgStat.pkg.id) {
                    pkgStat.label.get(context).lowercase().trim()
                }
                if (normalizedLabel.contains(queryNormalized)) return@filter true

                return@filter false
            }

        val maxAppSize = filteredPkgStats.values.maxOfOrNull { it.totalSize }

        val apps = filteredPkgStats
            .map { (installId, pkgStat) ->
                AppsItemVH.Item(
                    appCategory = category,
                    pkgStat = pkgStat,
                    sizeRatio = computeSizeBarRatio(pkgStat.totalSize, maxAppSize),
                    onItemClicked = {
                        navigateTo(AppDetailsRoute(
                            storageId = storage.id,
                            installId = installId,
                        ))
                    }
                )
            }
            .sortedByDescending { it.pkgStat.totalSize }

        State(
            storage = storage,
            apps = apps,
            isSearchActive = queryNormalized.isNotEmpty(),
            progress = progress,
        )
    }.asLiveData2()

    fun updateSearchQuery(query: String) {
        log(TAG) { "updateSearchQuery($query)" }
        searchQuery.value = query
    }

    data class State(
        val storage: DeviceStorage,
        val apps: List<AppsItemVH.Item>,
        val isSearchActive: Boolean = false,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Content", "Apps", "ViewModel")
    }
}