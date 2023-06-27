package eu.darken.sdmse.scheduler.ui.manager

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SchedulerManagerFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class SchedulerManagerFragment : Fragment3(R.layout.scheduler_manager_fragment) {

    override val vm: SchedulerManagerViewModel by viewModels()
    override val ui: SchedulerManagerFragmentBinding by viewBinding()
    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_info -> {
                        webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Scheduler")
                        true
                    }

                    else -> false
                }
            }
        }

        ui.mainAction.setOnClickListener { vm.createNew() }

        val adapter = SchedulerAdapter()
        ui.list.setupDefaults(adapter, dividers = false)

        vm.items.observe2(ui) {
            adapter.update(it.listItems)
            loadingOverlay.isGone = it.listItems != null
            list.isGone = it.listItems == null
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
