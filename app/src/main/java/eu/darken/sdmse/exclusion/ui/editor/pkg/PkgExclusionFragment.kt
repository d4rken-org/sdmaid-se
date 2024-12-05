package eu.darken.sdmse.exclusion.ui.editor.pkg

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.ExclusionEditorPkgFragmentBinding
import eu.darken.sdmse.exclusion.core.types.Exclusion


@AndroidEntryPoint
class PkgExclusionFragment : Fragment3(R.layout.exclusion_editor_pkg_fragment) {

    override val vm: PkgExclusionViewModel by viewModels()
    override val ui: ExclusionEditorPkgFragmentBinding by viewBinding()

    private val onBackPressedcallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            vm.cancel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedcallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.scrollView)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setNavigationOnClickListener { vm.cancel() }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_remove_exclusion -> {
                        vm.remove()
                        true
                    }

                    R.id.menu_action_save_exclusion -> {
                        vm.save()
                        true
                    }

                    else -> false
                }
            }
        }

        vm.state.observe2(ui) { state ->
            val exclusion = state.current
            toolbar.menu?.apply {
                findItem(R.id.menu_action_save_exclusion)?.isVisible = state.canSave
                findItem(R.id.menu_action_remove_exclusion)?.isVisible = state.canRemove
            }

            state.pkg?.let { icon.loadAppIcon(it) }

            primary.text = exclusion.label.get(requireContext())
            secondary.text = exclusion.pkgId.name

            ui.toolsAll.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.GENERAL)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.GENERAL) }
            }
            ui.toolsCorpsefinder.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.CORPSEFINDER)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.CORPSEFINDER) }
            }
            ui.toolsAppcleaner.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.APPCLEANER)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.APPCLEANER) }
            }
        }

        vm.events.observe2 {
            when (it) {
                is PkgExclusionEvents.RemoveConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(R.string.exclusion_editor_remove_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_remove_action) { _, _ ->
                        vm.remove(confirmed = true)
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                    }
                }.show()

                is PkgExclusionEvents.UnsavedChangesConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(R.string.exclusion_editor_unsaved_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_discard_action) { _, _ ->
                        vm.cancel(confirmed = true)
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                    }
                }.show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}