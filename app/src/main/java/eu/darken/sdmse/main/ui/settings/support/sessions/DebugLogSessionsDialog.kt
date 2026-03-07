package eu.darken.sdmse.main.ui.settings.support.sessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.BottomSheetDialogFragment2
import eu.darken.sdmse.databinding.DebugLogSessionsDialogBinding

@AndroidEntryPoint
class DebugLogSessionsDialog : BottomSheetDialogFragment2() {

    override val vm: DebugLogSessionsViewModel by viewModels()
    override lateinit var ui: DebugLogSessionsDialogBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = DebugLogSessionsDialogBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = DebugLogSessionsAdapter()
        ui.list.setupDefaults(adapter, verticalDividers = false)

        vm.state.observe2(ui) { state ->
            val items = state.sessions.map { session ->
                DebugLogSessionsAdapter.SessionItemVH.Item(
                    session = session,
                    onDelete = { vm.delete(session.id) },
                )
            }
            adapter.update(items)

            ui.list.isVisible = items.isNotEmpty()
            ui.emptyLabel.isVisible = items.isEmpty()
            ui.clearAllAction.isGone = items.isEmpty()
            ui.clearAllAction.isEnabled = state.sessions.any { it is DebugLogSession.Finished }
        }

        ui.clearAllAction.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(R.string.support_debuglog_folder_delete_confirmation_title)
                setMessage(R.string.support_debuglog_folder_delete_confirmation_message)
                setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                    vm.deleteAll()
                }
                setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
            }.show()
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
