package eu.darken.sdmse.systemcleaner.ui.details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3
import eu.darken.sdmse.systemcleaner.core.FilterContent
import androidx.core.os.bundleOf
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentFragment

class FilterContentDetailsPagerAdapter(
    private val activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<FilterContent>(activity, fm) {

    override fun getPageWidth(position: Int): Float = 1f / context.getSpanCount()

    override fun onCreateFragment(item: FilterContent): Fragment = FilterContentFragment().apply {
        arguments = bundleOf("identifier" to item.identifier)
    }

    override fun getPageTitle(position: Int): CharSequence = data[position].label.get(activity)

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            set.identifier == fragment.requireArguments().getString("identifier")
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}