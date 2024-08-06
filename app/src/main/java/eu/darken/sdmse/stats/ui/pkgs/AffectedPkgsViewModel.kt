package eu.darken.sdmse.stats.ui.pkgs

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.get
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.stats.core.AffectedPkg
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportId
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.ui.pkgs.elements.AffectedPkgVH
import eu.darken.sdmse.stats.ui.pkgs.elements.AffectedPkgsHeaderVH
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@HiltViewModel
class AffectedPkgsViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val statsRepo: StatsRepo,
    private val pkgRepo: PkgRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = AffectedPkgsFragmentArgs.fromSavedStateHandle(handle)

    private val reportId: ReportId
        get() = args.reportId

    private val report: Flow<Report> = flowOf(reportId)
        .map { statsRepo.getById(it) }
        .onEach { log(TAG) { "Retrieved report: $it" } }
        .filterNotNull()

    private val affectedPaths: Flow<Collection<AffectedPkg>> = flowOf(reportId)
        .map { statsRepo.getAffectedPkgs(it) }
        .onEach { log(TAG) { "Retrieved affected pkgs ${it.size} items" } }

    val state = combine(
        report,
        affectedPaths
    ) { report, affectedPkgs ->
        val elements = mutableListOf<AffectedPkgsAdapter.Item>()

        AffectedPkgsHeaderVH.Item(
            report = report,
            affectedPkgs = affectedPkgs,
        ).run { elements.add(this) }

        affectedPkgs
            .sortedBy { it.pkgId.name }
            .map { pkg -> AffectedPkgVH.Item(pkg, pkgRepo.get(pkg.pkgId).firstOrNull()) }
            .run { elements.addAll(this) }

        State(elements)
    }
        .onStart { emit(State()) }
        .asLiveData2()

    data class State(
        val elements: List<AffectedPkgsAdapter.Item>? = null,
    )

    companion object {
        private val TAG = logTag("Stats", "Affected", "Pkgs", "ViewModel")
    }
}