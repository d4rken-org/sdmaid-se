package eu.darken.sdmse.corpsefinder.ui.details

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.ui.details.corpse.CorpseFragment

class CorpseDetailsPagerAdapter(
    activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<Corpse>(activity, fm) {

    override fun getPageWidth(position: Int): Float = 1f / context.getSpanCount()

    override fun onCreateFragment(item: Corpse): Fragment = CorpseFragment().apply {
        arguments = bundleOf("identifier" to item.identifier)
    }

    override fun getPageTitle(position: Int): CharSequence = data[position].lookup.name

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            @Suppress("DEPRECATION")
            set.identifier == fragment.requireArguments().getParcelable<APath>("identifier")
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}
