package eu.darken.sdmse.common.previews

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.PagerAdapter
import eu.darken.sdmse.common.previews.item.PreviewItem
import eu.darken.sdmse.common.previews.item.PreviewItemFragment
import eu.darken.sdmse.common.previews.item.PreviewItemFragmentArgs
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3


class PreviewAdapter(
    activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<PreviewItem>(activity, fm) {

    override fun onCreateFragment(item: PreviewItem): Fragment = PreviewItemFragment().apply {
        arguments = PreviewItemFragmentArgs(item).toBundle()
    }

    override fun getPageTitle(position: Int): CharSequence = data[position].path.path

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            set.path == PreviewItemFragmentArgs.fromBundle(fragment.requireArguments()).item.path
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}