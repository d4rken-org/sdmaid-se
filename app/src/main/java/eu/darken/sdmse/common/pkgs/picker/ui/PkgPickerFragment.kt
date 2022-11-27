package eu.darken.sdmse.common.pkgs.picker.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getCountString
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.ClickMod
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.popBackStack
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.smart.Smart2Fragment
import eu.darken.sdmse.common.viewBinding
import eu.darken.sdmse.databinding.PkgPickerFragmentBinding
import eu.darken.sdmse.storage.ui.editor.StorageEditorResultListener
import javax.inject.Inject

@AndroidEntryPoint
class PkgPickerFragment : Smart2Fragment(R.layout.pkg_picker_fragment),
    StorageEditorResultListener {

    override val vdc: PkgPickerFragmentVDC by viewModels()
    override val ui: PkgPickerFragmentBinding by viewBinding()

    @Inject lateinit var adapter: PkgPickerAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.apply {
            storageList.setupDefaults(adapter)
            toolbar.apply {
                setupWithNavController(findNavController())
                setNavigationIcon(R.drawable.ic_baseline_close_24)
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.action_select_all -> {
                            vdc.selectAll()
                            true
                        }
                        R.id.action_unselect_all -> {
                            vdc.unselectAll()
                            true
                        }
                        else -> false
                    }
                }
            }

            fab.setOnClickListener { vdc.done() }
        }

        adapter.modules.add(ClickMod { _: ModularAdapter.VH, i: Int -> vdc.selectPkg(adapter.data[i]) })

        vdc.state.observe2(ui) { state ->
            adapter.update(state.items)
            toolbar.apply {
                subtitle = resources.getCountString(R.plurals.x_selected, state.selected.size)
                menu.apply {
                    findItem(R.id.action_select_all).isVisible = state.items.size != state.selected.size
                    findItem(R.id.action_unselect_all).isVisible = state.selected.isNotEmpty()
                }
            }
            fab.isInvisible = state.selected.isEmpty()
        }

        vdc.finishEvent.observe2(this) {
            setPkgPickerResult(it)
            popBackStack()
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
