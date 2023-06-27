package eu.darken.sdmse.appcontrol.ui.list.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.uix.BottomSheetDialogFragment2
import eu.darken.sdmse.databinding.AppcontrolActionDialogBinding

@AndroidEntryPoint
class AppActionDialog : BottomSheetDialogFragment2() {
    override val vm: AppActionViewModel by viewModels()
    override lateinit var ui: AppcontrolActionDialogBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = AppcontrolActionDialogBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = AppActionAdapter()
        // TODO fix dividers
        ui.recyclerview.setupDefaults(adapter, dividers = false)

        vm.state.observe2(ui) { (progress, appInfo, actions) ->
            val pkg = appInfo.pkg
            icon.loadAppIcon(pkg)
            primary.text = appInfo.label.get(requireContext())
            secondary.text = pkg.packageName
            tertiary.text = "${pkg.versionName} (${pkg.versionCode})"
            adapter.update(actions)

            tagSystem.tagSystem.isInvisible = !appInfo.pkg.isSystemApp
            tagDisabled.tagDisabled.isInvisible = appInfo.pkg.isEnabled
            tagContainer.isGone = tagContainer.children.none { it.isVisible }

            ui.recyclerview.isInvisible = progress != null
            ui.loadingOverlay.setProgress(progress)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                else -> {}
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}