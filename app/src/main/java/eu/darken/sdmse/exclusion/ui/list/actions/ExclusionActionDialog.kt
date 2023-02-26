package eu.darken.sdmse.exclusion.ui.list.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.BottomSheetDialogFragment2
import eu.darken.sdmse.databinding.ExclusionActionFragmentBinding
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
            when (state.exclusion) {
                is PackageExclusion -> {
                    primary.text = state.exclusion.pkgId.name
                    secondary.text = getString(R.string.exclusion_type_package)
                }
                is PathExclusion -> {
                    primary.text = state.exclusion.path.userReadablePath.get(requireContext())
                    secondary.text = getString(R.string.exclusion_type_path)
                }
                else -> throw NotImplementedError()
            }

            ui.deleteAction.setOnClickListener { vm.delete() }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}