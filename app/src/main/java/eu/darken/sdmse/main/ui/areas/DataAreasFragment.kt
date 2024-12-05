package eu.darken.sdmse.main.ui.areas

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DataAreasFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class DataAreasFragment : Fragment3(R.layout.data_areas_fragment) {

    override val vm: DataAreasViewModel by viewModels()
    override val ui: DataAreasFragmentBinding by viewBinding()
    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.list)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_refresh -> {
                        vm.reloadDataAreas()
                        true
                    }

                    R.id.menu_action_info -> {
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setMessage(R.string.data_areas_description)
                            setNeutralButton(eu.darken.sdmse.common.R.string.general_more_info_action) { _, _ ->
                                // TODO more direct link
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki")
                            }
                        }.show()
                        true
                    }

                    else -> false
                }
            }
        }

        val adapter = DataAreasAdapter()
        ui.list.setupDefaults(adapter)

        vm.items.observe2(ui) {
            adapter.update(it.areas)
            loadingOverlay.isGone = it.areas != null
            list.isGone = it.areas == null

            toolbar.menu?.findItem(R.id.menu_action_refresh)?.isVisible = it.allowReload
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
