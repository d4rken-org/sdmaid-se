package eu.darken.sdmse.appcontrol.ui.list.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.BottomSheetDialogFragment2
import eu.darken.sdmse.databinding.AppcontrolActionDialogBinding

@AndroidEntryPoint
class AppActionDialog : BottomSheetDialogFragment2() {
    override val vm: AppActionDialogVM by viewModels()
    override lateinit var ui: AppcontrolActionDialogBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = AppcontrolActionDialogBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = AppActionAdapter()
        // TODO fix dividers
        ui.recyclerview.setupDefaults(adapter, dividers = false)

        vm.state.observe2(ui) { state ->
            val pkg = state.appInfo.pkg
            icon.loadAppIcon(pkg)
            primary.text = state.appInfo.label.get(requireContext())
            secondary.text = pkg.packageName
            tertiary.text = "${pkg.versionName} (${pkg.versionCode})"
            adapter.update(state.actions)

            ui.recyclerview.isInvisible = state.isWorking
            ui.progressCircular.isInvisible = !state.isWorking
        }

        super.onViewCreated(view, savedInstanceState)
    }
}