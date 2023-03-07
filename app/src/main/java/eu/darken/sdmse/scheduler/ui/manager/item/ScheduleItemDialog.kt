package eu.darken.sdmse.scheduler.ui.manager.item

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.uix.BottomSheetDialogFragment2
import eu.darken.sdmse.databinding.SchedulerItemDialogBinding

@AndroidEntryPoint
class ScheduleItemDialog : BottomSheetDialogFragment2() {
    override val vm: ScheduleItemDialogVM by viewModels()
    override lateinit var ui: SchedulerItemDialogBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = SchedulerItemDialogBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(ui) { state ->
            val schedule = state.schedule

            ui.nameInput.setText(schedule.label)

            ui.toolCorpsefinderToggle.apply {
                isChecked = schedule.useCorpseFinder
                jumpDrawablesToCurrentState()
            }
            ui.toolSystemcleanerToggle.apply {
                isChecked = schedule.useSystemCleaner
                jumpDrawablesToCurrentState()
            }
            ui.toolAppcleanerToggle.apply {
                isChecked = schedule.useAppCleaner
                jumpDrawablesToCurrentState()
            }

            saveAction.setOnClickListener {
                vm.saveSchedule(
                    ui.nameInput.text?.toString() ?: "",
                    ui.toolCorpsefinderToggle.isChecked,
                    ui.toolSystemcleanerToggle.isChecked,
                    ui.toolAppcleanerToggle.isChecked,
                )
            }
            removeAction.apply {
                setOnClickListener { vm.deleteSchedule() }
                isVisible = !state.isNew
            }

            loadingOverlay.isVisible = false
            contentContainer.isVisible = true
        }

        super.onViewCreated(view, savedInstanceState)
    }
}