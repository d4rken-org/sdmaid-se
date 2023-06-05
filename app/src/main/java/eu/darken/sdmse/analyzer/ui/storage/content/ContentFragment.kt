package eu.darken.sdmse.analyzer.ui.storage.content

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AnalyzerContentFragmentBinding

@AndroidEntryPoint
class ContentFragment : Fragment3(R.layout.analyzer_content_fragment) {

    override val vm: ContentFragmentVM by viewModels()
    override val ui: AnalyzerContentFragmentBinding by viewBinding()

    private val onBackPressedcallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            vm.onNavigateBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedcallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setNavigationOnClickListener { vm.onNavigateBack() }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> false
                }
            }

        }

        val adapter = ContentAdapter()
        ui.list.setupDefaults(adapter)

        vm.state.observe2(ui) { state ->
            toolbar.title = state.title?.get(requireContext())
            toolbar.subtitle = state.subtitle?.get(requireContext())

            adapter.update(state.items)
            loadingOverlay.setProgress(state.progress)
            list.isInvisible = state.progress != null
        }

        vm.events.observe2 { event ->
            when (event) {
                is ContentItemEvents.ContentLongPressActions -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(
                        getString(
                            eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                            event.item.path.userReadablePath.get(context),
                        )
                    )
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                        vm.delete(setOf(event.item))
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }

                    setNeutralButton(
                        if (event.hasExclusion) R.string.exclusion_edit_action else R.string.exclusion_create_action
                    ) { _, _ ->
                        vm.openExclusion(event.item)
                    }
                }.show()

                is ContentItemEvents.ShowNoAccessHint -> Snackbar.make(
                    requireView(),
                    R.string.analyzer_content_access_opaque,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Analyzer", "Content", "Fragment")
    }
}
