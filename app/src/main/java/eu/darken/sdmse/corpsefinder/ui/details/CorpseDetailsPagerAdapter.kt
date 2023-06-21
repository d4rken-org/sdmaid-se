package eu.darken.sdmse.corpsefinder.ui.details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.ui.details.corpse.CorpseFragment
import eu.darken.sdmse.corpsefinder.ui.details.corpse.CorpseFragmentArgs

class CorpseDetailsPagerAdapter(
    activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<Corpse>(activity, fm) {

    override fun onCreateFragment(item: Corpse): Fragment = CorpseFragment().apply {
        arguments = CorpseFragmentArgs(item.identifier).toBundle()
    }

    override fun getPageTitle(position: Int): CharSequence = data[position].lookup.name

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            set.identifier == CorpseFragmentArgs.fromBundle(fragment.requireArguments()).identifier
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}