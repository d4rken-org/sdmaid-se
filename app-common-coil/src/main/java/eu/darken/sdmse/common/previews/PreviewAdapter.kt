package eu.darken.sdmse.common.previews

import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.common.previews.item.PreviewItem
import eu.darken.sdmse.common.previews.item.PreviewItemFragment
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3


class PreviewAdapter(
    activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<PreviewItem>(activity, fm) {

    override fun onCreateFragment(item: PreviewItem): Fragment = PreviewItemFragment().apply {
        arguments = bundleOf("item" to item)
    }

    override fun getPageTitle(position: Int): CharSequence = data[position].path.path

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            set.path == BundleCompat.getParcelable(fragment.requireArguments(), "item", PreviewItem::class.java)!!.path
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}
