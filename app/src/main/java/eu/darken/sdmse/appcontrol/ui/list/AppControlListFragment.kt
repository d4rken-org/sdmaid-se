package eu.darken.sdmse.appcontrol.ui.list

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.navigation.getSpanCount
import eu.darken.sdmse.common.setChecked2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AppcontrolListFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class AppControlListFragment : Fragment3(R.layout.appcontrol_list_fragment) {

    override val vm: AppControlListViewModel by viewModels()
    override val ui: AppcontrolListFragmentBinding by viewBinding()
    private var searchView: SearchView? = null
    private var showAppToggleActions: Boolean = false
    private var showAppForceStopActions: Boolean = false

    val DrawerLayout.isDrawerOpen: Boolean
        get() = isDrawerOpen(GravityCompat.END)

    fun DrawerLayout.toggle() = if (isDrawerOpen) closeDrawer(GravityCompat.END) else openDrawer(GravityCompat.END)

    private var currentSortMode: SortSettings.Mode = SortSettings.Mode.NAME
    private val onBackPressedcallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (ui.drawer.isDrawerOpen) ui.drawer.toggle()
        }
    }
    private var lastSelection: Collection<AppControlListAdapter.Item>? = null
    private lateinit var exportPath: ActivityResultLauncher<Intent>


    @Inject lateinit var adapter: AppControlListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedcallback)
        exportPath = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                log(TAG, WARN) { "exportPathPickerLauncher returned ${result.resultCode}: ${result.data}" }
                return@registerForActivityResult
            }
            log(TAG) { "lastSelection.size=${lastSelection?.size}" }
            lastSelection?.let { vm.export(it, result.data?.data) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            whole(ui.root)
        }
        ui.list.setupDefaults(
            adapter = adapter,
            horizontalDividers = true,
            layouter = GridLayoutManager(context, getSpanCount(), GridLayoutManager.VERTICAL, false)
        )

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
                findViewById<EditText>(androidx.appcompat.R.id.search_src_text)?.apply {
                    val fixedTextColor = requireContext().getColorForAttr(
                        com.google.android.material.R.attr.colorOnPrimary
                    )
                    setTextColor(fixedTextColor)
                    setHintTextColor(fixedTextColor)
                    if (hasApiLevel(29)) {
                        @SuppressLint("NewApi")
                        textCursorDrawable = textCursorDrawable?.apply {
                            DrawableCompat.setTint(this, fixedTextColor)
                        }
                    }
                }
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
                    ui.refreshAction.isInvisible = true
                }

                override fun onDrawerStateChanged(newState: Int) {
                    onBackPressedcallback.isEnabled = newState == DrawerLayout.STATE_IDLE && isDrawerOpen
                }

                override fun onDrawerOpened(drawerView: View) {

                }

                override fun onDrawerClosed(drawerView: View) {
                    ui.refreshAction.isInvisible = false
                }
            })
        }

        val tracker = installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_appcontrol_list_cab,
            onPrepare = { _: SelectionTracker<String>, _: ActionMode, menu: Menu ->
                menu.findItem(R.id.action_toggle_selection)?.isVisible = showAppToggleActions
                menu.findItem(R.id.action_forcestop_selection)?.isVisible = showAppForceStopActions
                true
            },
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: Collection<AppControlListAdapter.Item> ->
                if (selected.isEmpty()) return@installListSelection false

                when (item.itemId) {
                    R.id.action_exclude_selected -> {
                        vm.exclude(selected)
                        tracker.clearSelection()
                        true
                    }

                    R.id.action_toggle_selection -> {
                        vm.toggle(selected)
                        tracker.clearSelection()
                        true
                    }

                    R.id.action_uninstall_selection -> {
                        vm.uninstall(selected)
                        tracker.clearSelection()
                        true
                    }

                    R.id.action_export_selection -> {
                        vm.export(selected)
                        tracker.clearSelection()
                        true
                    }

                    R.id.action_forcestop_selection -> {
                        vm.forceStop(selected)
                        tracker.clearSelection()
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
                    SortSettings.Mode.SIZE -> getRowItem(pos)?.lablrSize
                }
                FastScrollItemIndicator.Text(lbl ?: "?")
            }
            val showIndicator: (FastScrollItemIndicator, Int, Int) -> Boolean = { _, _, size ->
                size in 2..32
            }
            fastscroller.setupWithRecyclerView(ui.list, itemLabler, showIndicator, true)
            fastscrollerThumb.setupWithFastScroller(ui.fastscroller)
        }

        ui.sortmodeDirection.setOnClickListener { vm.toggleSortDirection() }

        ui.apply {
            tagFilterClearAction.setOnClickListener { vm.clearTags() }
            tagFilterUserSwitch.setOnClickListener { vm.toggleTag(FilterSettings.Tag.USER) }
            tagFilterSystemSwitch.setOnClickListener { vm.toggleTag(FilterSettings.Tag.SYSTEM) }
            tagFilterEnabledSwitch.setOnClickListener { vm.toggleTag(FilterSettings.Tag.ENABLED) }
            tagFilterDisabledSwitch.setOnClickListener { vm.toggleTag(FilterSettings.Tag.DISABLED) }
            tagFilterActiveSwitch.setOnClickListener { vm.toggleTag(FilterSettings.Tag.ACTIVE) }
        }

        vm.state.observe2(ui) { state ->
            showAppToggleActions = state.allowAppToggleActions
            showAppForceStopActions = state.allowAppForceStopActions

            loadingOverlay.setProgress(state.progress)
            list.isGone = state.progress != null
            fastscroller.isInvisible = state.progress != null || state.appInfos.isNullOrEmpty()
            refreshAction.isInvisible = state.progress != null

            val checkedSortMode = when (state.options.listSort.mode) {
                SortSettings.Mode.NAME -> R.id.sortmode_name
                SortSettings.Mode.LAST_UPDATE -> R.id.sortmode_updated
                SortSettings.Mode.INSTALLED_AT -> R.id.sortmode_installed
                SortSettings.Mode.PACKAGENAME -> R.id.sortmode_packagename
                SortSettings.Mode.SIZE -> R.id.sortmode_size
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
                        R.id.sortmode_size -> SortSettings.Mode.SIZE
                        else -> throw IllegalArgumentException("Unknown sortmode $checkedId")
                    }
                    vm.updateSortMode(mode)
                }
            }
            sortmodeSize.isVisible = state.hasSizeInfo

            sortmodeDirection.setIconResource(
                if (state.options.listSort.reversed) R.drawable.ic_sort_descending_24 else R.drawable.ic_sort_ascending_24
            )
            currentSortMode = state.options.listSort.mode

            val listFilter = state.options.listFilter
            tagFilterUserSwitch.setChecked2(listFilter.tags.contains(FilterSettings.Tag.USER), animate = false)
            tagFilterSystemSwitch.setChecked2(listFilter.tags.contains(FilterSettings.Tag.SYSTEM), animate = false)
            tagFilterEnabledSwitch.setChecked2(listFilter.tags.contains(FilterSettings.Tag.ENABLED), animate = false)
            tagFilterDisabledSwitch.setChecked2(listFilter.tags.contains(FilterSettings.Tag.DISABLED), animate = false)

            tagFilterActiveSwitch.apply {
                setChecked2(listFilter.tags.contains(FilterSettings.Tag.ACTIVE), animate = false)
                isEnabled = state.hasActiveInfo
            }

            if (state.appInfos != null) {
                toolbar.subtitle = getQuantityString2(
                    eu.darken.sdmse.common.R.plurals.result_x_items, state.appInfos.size
                )
            } else if (state.progress != null) {
                toolbar.subtitle = getString(eu.darken.sdmse.common.R.string.general_progress_loading)
            } else {
                toolbar.subtitle = null
            }

            if (state.appInfos != null && state.progress == null) {
                adapter.update(state.appInfos)
            }
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is AppControlListEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(
                        if (event.items.size > 1) {
                            getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_selected_x_items,
                                event.items.size
                            )
                        } else {
                            getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                event.items.single().appInfo.label.get(requireContext())
                            )
                        }
                    )
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                        vm.uninstall(event.items, confirmed = true)
                        tracker.clearSelection()
                    }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                }.show()

                is AppControlListEvents.ExclusionsCreated -> Snackbar
                    .make(
                        requireView(),
                        getQuantityString2(R.plurals.exclusion_x_new_exclusions, event.count),
                        Snackbar.LENGTH_LONG
                    )
                    .setAction(eu.darken.sdmse.common.R.string.general_view_action) {
                        AppControlListFragmentDirections.goToExclusions().navigate()
                    }
                    .show()

                AppControlListEvents.ShowSizeSortCaveat -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(R.string.appcontrol_list_sortmode_size_caveat_msg)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_gotit_action) { _, _ ->
                        vm.ackSizeSortCaveat()
                    }
                }.show()

                is AppControlListEvents.ConfirmToggle -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.appcontrol_toggle_confirmation_title)
                    setMessage(getQuantityString2(R.plurals.appcontrol_toggle_confirmation_message_x, event.items.size))
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_continue) { _, _ ->
                        vm.toggle(event.items, confirmed = true)
                        tracker.clearSelection()
                    }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                }.show()

                is AppControlListEvents.ExportSelectPath -> {
                    lastSelection = event.items
                    exportPath.launch(event.intent)
                }

                is AppControlListEvents.ConfirmForceStop -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.appcontrol_force_stop_confirm_title)
                    setMessage(
                        getQuantityString2(R.plurals.appcontrol_force_stop_confirmation_message_x, event.items.size)
                    )
                    setPositiveButton(R.string.appcontrol_force_stop_action) { _, _ ->
                        vm.forceStop(event.items, confirmed = true)
                        tracker.clearSelection()
                    }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                }.show()

                is AppControlListEvents.ShowResult -> {
                    Snackbar.make(
                        requireView(),
                        event.result.primaryInfo.get(requireContext()),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }

        ui.refreshAction.apply {
            setOnClickListener {
                tracker.clearSelection()
                vm.refresh()
            }
            setOnLongClickListener {
                tracker.clearSelection()
                vm.refresh(refreshPkgCache = true)
                true
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("AppControl", "List")
    }
}
