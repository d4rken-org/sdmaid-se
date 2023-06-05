package eu.darken.sdmse.exclusion.ui.editor.pkg

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.ExclusionEditorPkgFragmentBinding
import eu.darken.sdmse.exclusion.core.types.Exclusion


@AndroidEntryPoint
class PkgExclusionFragment : Fragment3(R.layout.exclusion_editor_pkg_fragment) {

    override val vm: PkgExclusionVM by viewModels()
    override val ui: ExclusionEditorPkgFragmentBinding by viewBinding()

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

        super.onViewCreated(view, savedInstanceState)
    }
}