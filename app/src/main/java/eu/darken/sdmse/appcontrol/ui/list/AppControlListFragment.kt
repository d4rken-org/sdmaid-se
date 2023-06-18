package eu.darken.sdmse.appcontrol.ui.list

import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.core.view.isInvisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.setChecked2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AppcontrolListFragmentBinding

@AndroidEntryPoint
class AppControlListFragment : Fragment3(R.layout.appcontrol_list_fragment) {

    override val vm: AppControlListFragmentVM by viewModels()
    override val ui: AppcontrolListFragmentBinding by viewBinding()
    private var searchView: SearchView? = null
    private var showRootActions: Boolean = false

    val DrawerLayout.isDrawerOpen: Boolean
        get() = isDrawerOpen(GravityCompat.END)

    fun DrawerLayout.toggle() = if (isDrawerOpen) closeDrawer(GravityCompat.END) else openDrawer(GravityCompat.END)

    private var currentSortMode: SortSettings.Mode = SortSettings.Mode.NAME
    private val onBackPressedcallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (ui.drawer.isDrawerOpen) ui.drawer.toggle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedcallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_filterpane -> {
                        ui.drawer.toggle()
                        true
                    }

                    else -> super.onOptionsItemSelected(it)
                }
            }
            menu.findItem(R.id.action_search)?.actionView?.apply {
                this as SearchView
                searchView = this

                queryHint = getString(eu.darken.sdmse.common.R.string.general_search_action)
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

        ui.drawer.apply {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            addDrawerListener(object : DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {

                }

                override fun onDrawerStateChanged(newState: Int) {
                    onBackPressedcallback.isEnabled = newState == DrawerLayout.STATE_IDLE && isDrawerOpen
                }

                override fun onDrawerOpened(drawerView: View) {

                }

                override fun onDrawerClosed(drawerView: View) {

                }

            })
        }

        val adapter = AppControlListAdapter()

        ui.list.setupDefaults(adapter)

        val tracker = installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_appcontrol_list_cab,
            onPrepare = { mode: ActionMode, menu: Menu ->
                menu.findItem(R.id.action_toggle_selection)?.isVisible = showRootActions
                true
            },
            onSelected = { mode: ActionMode, item: MenuItem, selected: Collection<AppControlListAdapter.Item> ->
                when (item.itemId) {
                    R.id.action_exclude_selected -> {
                        vm.exclude(selected)
                        mode.finish()
                        true
                    }

                    R.id.action_toggle_selection -> {
                        vm.toggle(selected)
                        mode.finish()
                        true
                    }

                    R.id.action_uninstall_selection -> {
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                            setMessage(
                                if (selected.size > 1) {
                                    getString(
                                        eu.darken.sdmse.common.R.string.general_delete_confirmation_message_selected_x_items,
                                        selected.size
                                    )
                                } else {
                                    getString(
                                        eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                        selected.single().appInfo.label.get(requireContext())
                                    )
                                }
                            )
                            setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                                vm.uninstall(selected)
                                mode.finish()
                            }
                            setNeutralButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                        }.show()
                        true
                    }

                    else -> false
                }
            }
        )

        ui.apply {
            val itemLabler: (Int) -> FastScrollItemIndicator? = { pos ->
                val getRowItem: (Int) -> AppControlListRowVH.Item? = {
                    adapter.data.getOrNull(pos) as? AppControlListRowVH.Item
                }
                val lbl = when (currentSortMode) {
                    SortSettings.Mode.NAME -> getRowItem(pos)?.lablrName
                    SortSettings.Mode.PACKAGENAME -> getRowItem(pos)?.lablrPkg
                    SortSettings.Mode.LAST_UPDATE -> getRowItem(pos)?.lablrUpdated
                    SortSettings.Mode.INSTALLED_AT -> getRowItem(pos)?.lablrInstalled
                }
                FastScrollItemIndicator.Text(lbl ?: "?")
            }
            val showIndicator: (FastScrollItemIndicator, Int, Int) -> Boolean = { indicator, index, size ->
                size in 2..32
            }
            fastscroller.setupWithRecyclerView(ui.list, itemLabler, showIndicator, true)
            fastscrollerThumb.setupWithFastScroller(ui.fastscroller)
        }

        ui.sortmodeDirection.setOnClickListener { vm.toggleSortDirection() }

        ui.apply {
            tagFilterUserSwitch.setOnClickListener { vm.toggleTag(FilterSettings.Tag.USER) }
            tagFilterSystemSwitch.setOnClickListener { vm.toggleTag(FilterSettings.Tag.SYSTEM) }
            tagFilterEnabledSwitch.setOnClickListener { vm.toggleTag(FilterSettings.Tag.ENABLED) }
            tagFilterDisabledSwitch.setOnClickListener { vm.toggleTag(FilterSettings.Tag.DISABLED) }
        }

        vm.state.observe2(ui) { state ->
            showRootActions = state.showRootActions

            loadingOverlay.setProgress(state.progress)
            list.isInvisible = state.progress != null
            fastscroller.isInvisible = state.progress != null || state.appInfos.isNullOrEmpty()
            refreshAction.isInvisible = state.progress != null

            val checkedSortMode = when (state.listSort.mode) {
                SortSettings.Mode.NAME -> R.id.sortmode_name
                SortSettings.Mode.LAST_UPDATE -> R.id.sortmode_updated
                SortSettings.Mode.INSTALLED_AT -> R.id.sortmode_installed
                SortSettings.Mode.PACKAGENAME -> R.id.sortmode_packagename
            }
            sortmodeGroup.apply {
                clearOnButtonCheckedListeners()
                check(checkedSortMode)
                addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (!isChecked) return@addOnButtonCheckedListener
                    val mode = when (checkedId) {
                        R.id.sortmode_name -> SortSettings.Mode.NAME
                        R.id.sortmode_updated -> SortSettings.Mode.LAST_UPDATE
                        R.id.sortmode_installed -> SortSettings.Mode.INSTALLED_AT
                        R.id.sortmode_packagename -> SortSettings.Mode.PACKAGENAME
                        else -> throw IllegalArgumentException("Unknown sortmode $checkedId")
                    }
                    vm.updateSortMode(mode)
                }
            }

            sortmodeDirection.setIconResource(
                if (state.listSort.reversed) R.drawable.ic_sort_descending_24 else R.drawable.ic_sort_ascending_24
            )
            currentSortMode = state.listSort.mode

            val listFilter = state.listFilter
            tagFilterUserSwitch.setChecked2(listFilter.tags.contains(FilterSettings.Tag.USER), animate = false)
            tagFilterSystemSwitch.setChecked2(listFilter.tags.contains(FilterSettings.Tag.SYSTEM), animate = false)
            tagFilterEnabledSwitch.setChecked2(listFilter.tags.contains(FilterSettings.Tag.ENABLED), animate = false)
            tagFilterDisabledSwitch.setChecked2(listFilter.tags.contains(FilterSettings.Tag.DISABLED), animate = false)

            if (state.appInfos != null) {
                toolbar.subtitle = getQuantityString2(
                    eu.darken.sdmse.common.R.plurals.result_x_items, state.appInfos.size
                )
                adapter.update(state.appInfos)
            } else {
                toolbar.subtitle = null
            }
        }

        vm.events.observe2(ui) {
            when (it) {
                is AppControlListEvents.ConfirmDeletion -> {}
                is AppControlListEvents.ExclusionsCreated -> Snackbar
                    .make(
                        requireView(),
                        getQuantityString2(R.plurals.exclusion_x_new_exclusions, it.count),
                        Snackbar.LENGTH_LONG
                    )
                    .setAction(eu.darken.sdmse.common.R.string.general_view_action) {
                        AppControlListFragmentDirections.goToExclusions().navigate()
                    }
                    .show()
            }
        }

        ui.refreshAction.setOnClickListener {
            tracker.clearSelection()
            vm.refresh()
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("AppControl", "List")
    }
}
