package eu.darken.sdmse.setup.saf

import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.SimpleVHCreatorMod
import eu.darken.sdmse.databinding.SetupSafItemPathBinding
import javax.inject.Inject


class SAFCardPathAdapter @Inject constructor() :
    ModularAdapter<SAFCardPathAdapter.VH>(),
    HasAsyncDiffer<SAFCardPathAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(SimpleVHCreatorMod { VH(it) })
    }

    data class Item(
        val pathAccess: SAFSetupModule.Result.PathAccess,
        val onClicked: (SAFSetupModule.Result.PathAccess) -> Unit,
    ) : DifferItem {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

    class VH(parent: ViewGroup) :
        ModularAdapter.VH(R.layout.setup_saf_item_path, parent),
        BindableVH<Item, SetupSafItemPathBinding> {

        override val viewBinding = lazy { SetupSafItemPathBinding.bind(itemView) }

        override val onBindData: SetupSafItemPathBinding.(
            item: Item,
            payloads: List<Any>
        ) -> Unit = { item, _ ->
            icon.apply {
                setImageResource(
                    if (item.pathAccess.hasAccess) R.drawable.folder_lock_open else R.drawable.folder_lock
                )
                imageTintList = if (item.pathAccess.hasAccess) {
                    ColorStateList.valueOf(context.getColorForAttr(com.google.android.material.R.attr.colorPrimary))
                } else {
                    ColorStateList.valueOf(context.getColorForAttr(androidx.appcompat.R.attr.colorControlNormal))
                }
            }

            primary.text = item.pathAccess.label.get(context)
            secondary.text = item.pathAccess.localPath.userReadablePath.get(context)
            tertiary.isVisible = item.pathAccess.hasAccess
            itemView.setOnClickListener { item.onClicked(item.pathAccess) }
        }

    }
}