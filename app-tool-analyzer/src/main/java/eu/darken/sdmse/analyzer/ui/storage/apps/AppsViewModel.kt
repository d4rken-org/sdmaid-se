package eu.darken.sdmse.analyzer.ui.storage.apps

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.AppDetailsRoute
import eu.darken.sdmse.analyzer.ui.AppsRoute
import eu.darken.sdmse.analyzer.ui.storage.computeSizeBarRatio
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("StaticFieldLeak")
@HiltViewModel
class AppsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val analyzer: Analyzer,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val routeFlow = MutableStateFlow<AppsRoute?>(null)
    private val searchQuery = MutableStateFlow("")

    private var lastCategory: AppCategory? = null
    private val queryCacheLabel = mutableMapOf<Pkg.Id, String>()
    private val queryCachePkg = mutableMapOf<Pkg.Id, String>()

    fun bindRoute(route: AppsRoute) {
        if (routeFlow.value != null) return
        log(TAG, INFO) { "bindRoute(${route.storageId})" }
        routeFlow.value = route
    }

    init {
        // Process-death handler: pop when target app category is missing.
        routeFlow
            .filterNotNull()
            .flatMapLatest { route ->
                analyzer.data
                    .filter { it.findAppCategory(route) == null }
                    .take(1)
                    .onEach {
                        log(TAG, WARN) { "Can't find app category for ${route.storageId}" }
                        navUp()
                    }
            }
            .launchInViewModel()
    }

    private fun Analyzer.Data.findAppCategory(route: AppsRoute): AppCategory? {
        return categories[route.storageId]?.filterIsInstance<AppCategory>()?.singleOrNull()
    }

    val state: StateFlow<State> = routeFlow
        .filterNotNull()
        .flatMapLatest { route ->
            combine(
                analyzer.data.filter { it.findAppCategory(route) != null },
                analyzer.progress,
                searchQuery,
            ) { data, progress, query ->
                val storage = data.storages.single { it.id == route.storageId }
                val category = data.findAppCategory(route)!!
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
                    .map { (_, pkgStat) ->
                        Row(
                            pkgStat = pkgStat,
                            sizeRatio = computeSizeBarRatio(pkgStat.totalSize, maxAppSize),
                        )
                    }
                    .sortedByDescending { it.pkgStat.totalSize }

                State(
                    storage = storage,
                    apps = apps,
                    isSearchActive = queryNormalized.isNotEmpty(),
                    progress = progress,
                )
            }
        }
        .safeStateIn(initialValue = State(), onError = { State() })

    fun updateSearchQuery(query: String) {
        log(TAG) { "updateSearchQuery($query)" }
        searchQuery.value = query
    }

    fun onAppClick(row: Row) {
        val storageId = routeFlow.value?.storageId ?: return
        log(TAG) { "onAppClick(${row.pkgStat.id})" }
        navTo(AppDetailsRoute(storageId = storageId, installId = row.pkgStat.id))
    }

    data class Row(
        val pkgStat: AppCategory.PkgStat,
        val sizeRatio: Float?,
    )

    data class State(
        val storage: DeviceStorage? = null,
        val apps: List<Row> = emptyList(),
        val isSearchActive: Boolean = false,
        val progress: Progress.Data? = null,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Content", "Apps", "ViewModel")
    }
}
