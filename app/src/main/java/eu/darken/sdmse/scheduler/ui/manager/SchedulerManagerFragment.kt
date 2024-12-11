package eu.darken.sdmse.scheduler.ui.manager

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SchedulerCommandsEditDialogBinding
import eu.darken.sdmse.databinding.SchedulerManagerFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class SchedulerManagerFragment : Fragment3(R.layout.scheduler_manager_fragment) {

    override val vm: SchedulerManagerViewModel by viewModels()
    override val ui: SchedulerManagerFragmentBinding by viewBinding()
    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.list)
            bottomHalf(ui.mainActionContainer)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_info -> {
                        webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Scheduler")
                        true
                    }

                    R.id.menu_debug_schedule -> {
                        vm.debugSchedule()
                        true
                    }

                    else -> false
                }
            }
            menu?.findItem(R.id.menu_debug_schedule)?.isVisible = Bugs.isDebug
        }

        ui.mainAction.setOnClickListener { vm.createNew() }

        val adapter = SchedulerAdapter()
        ui.list.setupDefaults(adapter, verticalDividers = false)

        vm.items.observe2(ui) {
            adapter.update(it.listItems)
            loadingOverlay.isGone = it.listItems != null
            list.isGone = it.listItems == null
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is SchedulerManagerEvents.FinalCommandsEdit -> {
                    MaterialAlertDialogBuilder(requireContext()).apply {
                        val binding = SchedulerCommandsEditDialogBinding.inflate(layoutInflater, null, false)
                        binding.commandInput.setText(event.schedule.commandsAfterSchedule.joinToString("\n"))
                        setView(binding.root)

                        setTitle(R.string.scheduler_commands_after_schedule_label)
                        setMessage(R.string.scheduler_commands_after_schedule_desc)
                        setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
                            val cmdsRaw = binding.commandInput.text?.toString() ?: ""
                            vm.updateCommandsAfterSchedule(event.schedule.id, cmdsRaw)
                        }
                        setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    }.show()
                }

                is SchedulerManagerEvents.ShowBatteryOptimizationSettings -> {
                    try {
                        startActivity(event.intent)
                    } catch (e: ActivityNotFoundException) {
                        e.asErrorDialogBuilder(requireActivity()).show()
                    }
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
