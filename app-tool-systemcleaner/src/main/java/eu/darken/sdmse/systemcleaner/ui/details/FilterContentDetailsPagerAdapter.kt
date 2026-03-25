package eu.darken.sdmse.systemcleaner.ui.details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentFragment
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentViewModel

class FilterContentDetailsPagerAdapter(
    private val activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<FilterContent>(activity, fm) {

    override fun getPageWidth(position: Int): Float = 1f / context.getSpanCount()

    override fun onCreateFragment(item: FilterContent): Fragment = FilterContentFragment().apply {
        arguments = FilterContentViewModel.Args(identifier = item.identifier).toBundle()
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