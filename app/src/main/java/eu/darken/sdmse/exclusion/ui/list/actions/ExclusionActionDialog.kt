package eu.darken.sdmse.exclusion.ui.list.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.BottomSheetDialogFragment2
import eu.darken.sdmse.databinding.ExclusionActionFragmentBinding
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PackageExclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion

@AndroidEntryPoint
class ExclusionActionDialog : BottomSheetDialogFragment2() {
    override val vm: ExclusionActionDialogVM by viewModels()
    override lateinit var ui: ExclusionActionFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = ExclusionActionFragmentBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(ui) { state ->
            val exclusion = state.exclusion
            when (exclusion) {
                is PackageExclusion -> {
                    typeIcon.setImageResource(R.drawable.ic_app_extra_24)

                    icon.apply {
                        setImageResource(eu.darken.sdmse.common.io.R.drawable.ic_default_app_icon_24)
                        isGone = false
                    }
                    primary.text = exclusion.label.get(requireContext())
                    secondary.text = exclusion.pkgId.name
                    type.text = getString(R.string.exclusion_type_package)
                }
                is PathExclusion -> {
                    typeIcon.setImageResource(R.drawable.ic_file)

                    icon.isGone = true
                    primary.text = exclusion.label.get(requireContext())
                    secondary.text = exclusion.path.pathType.name
                    type.text = getString(R.string.exclusion_type_path)
                }
                else -> throw NotImplementedError()
            }

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
            ui.deleteAction.apply {
                isEnabled = state.canRemove
                setOnClickListener { vm.delete() }
            }
            ui.cancelAction.setOnClickListener {
                vm.cancel()
            }
            ui.saveAction.apply {
                isEnabled = state.canSave
                setOnClickListener { vm.save() }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}