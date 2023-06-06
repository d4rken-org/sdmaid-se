package eu.darken.sdmse.exclusion.ui.list

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.ExclusionListFragmentBinding
import eu.darken.sdmse.exclusion.ui.editor.segment.SegmentExclusionEditorOptions
import javax.inject.Inject

@AndroidEntryPoint
class ExclusionListFragment : Fragment3(R.layout.exclusion_list_fragment) {

    override val vm: ExclusionListFragmentVM by viewModels()
    override val ui: ExclusionListFragmentBinding by viewBinding()
    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_info -> {
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setMessage(R.string.exclusion_explanation_body1)
                            setNeutralButton(eu.darken.sdmse.common.R.string.general_more_info_action) { _, _ ->
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Exclusions")
                            }
                        }.show()
                        true
                    }

                    else -> false
                }
            }
        }

        ui.mainAction.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                val actions = arrayOf(
                    getString(R.string.exclusion_type_package),
                    getString(R.string.exclusion_type_path),
                    getString(R.string.exclusion_type_segment),
                )
                setItems(actions) { dialog, which ->
                    when (which) {
                        0 -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.exclusion_create_pkg_hint,
                                Toast.LENGTH_LONG
                            ).show()
                            ExclusionListFragmentDirections.goToAppControlListFragment().navigate()
                        }

                        1 -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.exclusion_create_path_hint,
                                Toast.LENGTH_LONG
                            ).show()
                            ExclusionListFragmentDirections.goToDeviceStorageFragment().navigate()
                        }

                        2 -> ExclusionListFragmentDirections.actionExclusionsListFragmentToSegmentExclusionFragment(
                            exclusionId = null,
                            initial = SegmentExclusionEditorOptions(
                                targetSegments = segs("DCIM", "Camera")
                            )
                        ).navigate()
                    }
                }
                setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                }
            }.show()
        }

        val adapter = ExclusionListAdapter()
        ui.list.setupDefaults(adapter)

        vm.state.observe2(ui) {
            adapter.update(it.items)
            loadingOverlay.isVisible = false
            emptyOverlay.isVisible = it.items.isEmpty()
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
