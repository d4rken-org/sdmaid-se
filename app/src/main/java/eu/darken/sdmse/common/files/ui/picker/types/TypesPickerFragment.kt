package eu.darken.sdmse.common.files.ui.picker.types

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.ui.picker.SharedPathPickerVM
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.ClickMod
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.lists.update
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.smart.SmartFragment
import eu.darken.sdmse.common.viewBinding
import eu.darken.sdmse.databinding.PathPickerTypesFragmentBinding
import eu.darken.sdmse.storage.ui.editor.types.StorageTypeAdapter
import javax.inject.Inject

@AndroidEntryPoint
class TypesPickerFragment : SmartFragment(R.layout.path_picker_types_fragment) {

    val navArgs by navArgs<TypesPickerFragmentArgs>()
    private val ui: PathPickerTypesFragmentBinding by viewBinding()
    private val vdc: TypesPickerFragmentVDC by viewModels()


    @Inject lateinit var adapter: StorageTypeAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter.modules.add(ClickMod { _: ModularAdapter.VH, i: Int -> vdc.selectType(adapter.data[i]) })
        ui.picktypeList.setupDefaults(adapter)

        vdc.state.observe2(this) {
            adapter.update(it.allowedTypes)
        }

        val sharedVM = ViewModelProvider(requireActivity()).get(SharedPathPickerVM::class.java)
        vdc.typeEvents.observe2(this) {
            sharedVM.launchType(it)
        }
        super.onViewCreated(view, savedInstanceState)
    }

}
