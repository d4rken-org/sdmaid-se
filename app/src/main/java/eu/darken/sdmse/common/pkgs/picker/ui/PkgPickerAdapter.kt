package eu.darken.sdmse.common.pkgs.picker.ui

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.*
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.SimpleVHCreatorMod
import eu.darken.sdmse.common.pkgs.NormalPkg
import eu.darken.sdmse.common.previews.AppPreviewRequest
import eu.darken.sdmse.common.previews.GlideApp
import eu.darken.sdmse.common.previews.into
import eu.darken.sdmse.databinding.PkgPickerAdapterLineBinding
import javax.inject.Inject

class PkgPickerAdapter @Inject constructor() : ModularAdapter<PkgPickerAdapter.VH>(),
    HasAsyncDiffer<PkgPickerAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<PkgPickerAdapter, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(SimpleVHCreatorMod { VH(it) })
    }

    data class Item(
        val pkg: NormalPkg,
        val label: String,
        val isSelected: Boolean
    ) : DifferItem {
        override val stableId: Long
            get() = pkg.packageName.hashCode().toLong()
    }

    class VH(parent: ViewGroup) : ModularAdapter.VH(R.layout.pkg_picker_adapter_line, parent),
        BindableVH<Item, PkgPickerAdapterLineBinding> {

        override val viewBinding = lazy { PkgPickerAdapterLineBinding.bind(itemView) }

        override val onBindData: PkgPickerAdapterLineBinding.(
            item: Item,
            payloads: List<Any>
        ) -> Unit = onBindData@{ item, _ ->
            name.text = item.label
            description.text = item.pkg.packageName
            checkbox.isChecked = item.isSelected

            GlideApp.with(context)
                .load(AppPreviewRequest(item.pkg, context))
                .into(previewContainer)
        }
    }

}
