package eu.darken.sdmse.appcontrol.ui.list

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AppcontrolListFragmentBinding

@AndroidEntryPoint
class AppControlListFragment : Fragment3(R.layout.appcontrol_list_fragment) {

    override val vm: AppControlListFragmentVM by viewModels()
    override val ui: AppcontrolListFragmentBinding by viewBinding()
    private var searchView: SearchView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
            menu.findItem(R.id.action_search)?.actionView?.apply {
                this as SearchView
                searchView = this

                queryHint = getString(R.string.general_search_action)
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        return false
                    }

                    override fun onQueryTextChange(query: String): Boolean {
                        vm.updateSearchQuery(query)
                        return false
                    }
                })
            }
        }

        val adapter = AppControlListAdapter()
        ui.list.setupDefaults(adapter)

        vm.items.observe2(ui) { state ->
            loadingOverlay.setProgress(state.progress)
            list.isInvisible = state.progress != null

            if (state.appInfos != null) {
                toolbar.subtitle = requireContext().getQuantityString2(R.plurals.result_x_items, state.appInfos.size)
                adapter.update(state.appInfos)
                searchView?.let {
                    if (it.query != state.searchQuery) {
                        it.setQuery(state.searchQuery, false)
                    }
                }
            } else {
                toolbar.subtitle = null
            }
        }

        vm.events.observe2(ui) {
            when (it) {

            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("AppControl", "List")
    }
}
