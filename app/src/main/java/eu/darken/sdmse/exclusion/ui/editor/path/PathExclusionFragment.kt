package eu.darken.sdmse.exclusion.ui.editor.path

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.ExclusionEditorPathFragmentBinding
import eu.darken.sdmse.exclusion.core.types.Exclusion


@AndroidEntryPoint
class PathExclusionFragment : Fragment3(R.layout.exclusion_editor_path_fragment) {

    override val vm: PathExclusionVM by viewModels()
    override val ui: ExclusionEditorPathFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
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
            val exclusion = state.exclusion
            toolbar.menu?.apply {
                findItem(R.id.menu_action_save_exclusion)?.isEnabled = state.canSave
                findItem(R.id.menu_action_remove_exclusion)?.isEnabled = state.canRemove
            }

            icon.isGone = true
            primary.text = exclusion.label.get(requireContext())
            secondary.text = exclusion.path.pathType.name

            ui.toolsAll.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.GENERAL)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.GENERAL) }
            }
            ui.toolsCorpsefinder.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.CORPSEFINDER)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.CORPSEFINDER) }
            }
            ui.toolsSystemcleaner.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.SYSTEMCLEANER)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.SYSTEMCLEANER) }
            }
            ui.toolsAppcleaner.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.APPCLEANER)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.APPCLEANER) }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}