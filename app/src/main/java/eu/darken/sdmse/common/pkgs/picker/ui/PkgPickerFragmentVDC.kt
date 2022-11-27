package eu.darken.sdmse.common.pkgs.picker.ui

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.NormalPkg
import eu.darken.sdmse.common.pkgs.picker.core.PickedPkg
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.smart.Smart2VDC
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PkgPickerFragmentVDC @Inject constructor(
    handle: SavedStateHandle,
    private val pkgOps: PkgOps,
    dispatcherProvider: DispatcherProvider,
) : Smart2VDC(dispatcherProvider) {

    private val navArgs by handle.navArgs<PkgPickerFragmentArgs>()
    private val options = navArgs.options

    private val pkgData: Flow<List<NormalPkg>> = flow {
        val pkgs = pkgOps.listPkgs()
            .filterIsInstance<NormalPkg>()
            .filter { options.allowSystemApps || !it.isSystemApp }
        emit(pkgs)
    }.replayingShare(vdcScope)

    private val selectedItems = MutableStateFlow(emptyList<String>())

    val state = combine(pkgData, selectedItems) { pkgs, selected ->
        PkgsState(
            items = pkgs.map { pkg ->
                PkgPickerAdapter.Item(
                    pkg = pkg,
                    label = pkg.getLabel(pkgOps) ?: pkg.packageName,
                    isSelected = selected.any { it == pkg.packageName }
                )
            },
            selected = selected,
        )
    }.asLiveData2()

    val finishEvent = SingleLiveEvent<PkgPickerResult?>()

    fun selectPkg(item: PkgPickerAdapter.Item) {
        log(TAG) { "selectPkg(item=$item)" }
        selectedItems.value
            .let {
                val newSelection = when {
                    item.isSelected -> it.minus(item.pkg.packageName)
                    else -> it.plus(item.pkg.packageName)
                }
                if (newSelection.size > options.selectionLimit) {
                    newSelection.subList(1, options.selectionLimit + 1)
                } else {
                    newSelection
                }
            }
            .run { selectedItems.value = this }
    }

    fun done() {
        PkgPickerResult(
            options = options,
            error = null,
            selection = selectedItems.value.map { PickedPkg(it) }.toSet(),
            payload = Bundle(),
        ).run { finishEvent.postValue(this) }
    }

    fun selectAll() = launch {
        selectedItems.value = (pkgData.first().map { it.packageName })
    }

    fun unselectAll() = launch {
        selectedItems.value = emptyList()
    }

    data class PkgsState(
        val items: List<PkgPickerAdapter.Item> = emptyList(),
        val selected: List<String> = emptyList()
    )

    companion object {
        private val TAG = logTag("Pkg", "Picker", "VDC")
    }
}