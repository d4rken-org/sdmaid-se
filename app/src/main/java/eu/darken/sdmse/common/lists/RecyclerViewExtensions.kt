package eu.darken.sdmse.common.lists

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.MenuRes
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import eu.darken.sdmse.R
import eu.darken.sdmse.common.DividerItemDecoration2
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.selection.ItemSelectionKeyProvider
import eu.darken.sdmse.common.lists.selection.ItemSelectionLookup
import eu.darken.sdmse.common.lists.selection.ItemSelectionMod
import eu.darken.sdmse.common.lists.selection.SelectableItem
import me.zhanghai.android.fastscroll.FastScrollerBuilder

fun RecyclerView.setupDefaults(
    adapter: RecyclerView.Adapter<*>? = null,
    dividers: Boolean = true,
    fastscroll: Boolean = true,
) = apply {
    layoutManager = LinearLayoutManager(context)
    itemAnimator = DefaultItemAnimator()
    if (dividers) addItemDecoration(
        DividerItemDecoration2(
            context,
            DividerItemDecoration.VERTICAL,
            drawAfterLastItem = false
        )
    )
    if (adapter != null) this.adapter = adapter

    if (fastscroll) {
        FastScrollerBuilder(this).apply {
            useMd2Style()
        }.build()
    }
}

fun <AdapterT> RecyclerView.setupSelectionBase(
    tag: String,
    adapter: AdapterT,
    selectionPredicate: SelectionTracker.SelectionPredicate<String> = SelectionPredicates.createSelectAnything()
): SelectionTracker<String> where AdapterT : DataAdapter<*>, AdapterT : ModularAdapter<*> {
    log(tag) { "Setting up selection $tag on $this with $adapter" }

    val list = this
    val tracker = SelectionTracker
        .Builder(
            tag,
            list,
            ItemSelectionKeyProvider(adapter),
            ItemSelectionLookup(list),
            StorageStrategy.createStringStorage(),
        )
        .withSelectionPredicate(selectionPredicate)
        .build()

    adapter.addMod(ItemSelectionMod(tracker))

    return tracker
}

fun <AdapterT, ItemT : SelectableItem> RecyclerView.setupSelectionCommon(
    tag: String,
    adapter: AdapterT,
    toolbar: MaterialToolbar,
    @MenuRes cabMenuRes: Int,
    onPrepare: (ActionMode, Menu) -> Boolean,
    onSelected: (ActionMode, MenuItem, Collection<ItemT>) -> Boolean,
    selectionPredicate: SelectionTracker.SelectionPredicate<String> = SelectionPredicates.createSelectAnything()
): SelectionTracker<String> where AdapterT : DataAdapter<ItemT>, AdapterT : ModularAdapter<*> {
    val tracker = setupSelectionBase(tag, adapter, selectionPredicate)
    log(tag) { "Performing common setup for $toolbar" }

    var actionMode: ActionMode? = null
    val cabCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(cabMenuRes, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.findItem(R.id.action_select_all)?.isVisible = tracker.selection.size() != adapter.data.size
            onPrepare(mode, menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, menuItem: MenuItem): Boolean = when (menuItem.itemId) {
            R.id.action_select_all -> {
                val items = adapter.data.mapNotNull { it.itemSelectionKey }
                tracker.setItemsSelected(items, true)
                true
            }

            else -> {
                val selectedItems = tracker.selection.map { key ->
                    adapter.data.first { it.itemSelectionKey == key }
                }
                onSelected(mode, menuItem, selectedItems)
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            tracker.clearSelection()
            actionMode = null
        }
    }

    tracker.addObserver(object : SelectionTracker.SelectionObserver<String>() {
        override fun onSelectionChanged() {
            when {
                tracker.hasSelection() -> {
                    actionMode ?: toolbar.startActionMode(cabCallback)?.also { actionMode = it }
                    actionMode?.apply {
                        title = context.getQuantityString2(
                            eu.darken.sdmse.common.R.plurals.result_x_items,
                            tracker.selection.size()
                        )
                        invalidate()
                    }
                }

                else -> {
                    actionMode?.finish()
                    actionMode = null
                }
            }
        }
    })

    return tracker
}