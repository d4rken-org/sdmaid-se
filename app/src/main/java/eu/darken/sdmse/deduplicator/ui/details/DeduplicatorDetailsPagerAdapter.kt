package eu.darken.sdmse.deduplicator.ui.details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterFragment
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterFragmentArgs

class DeduplicatorDetailsPagerAdapter(
    activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<Duplicate.Cluster>(activity, fm) {

    override fun getPageWidth(position: Int): Float = 1f / context.getSpanCount()

    override fun onCreateFragment(item: Duplicate.Cluster): Fragment = ClusterFragment().apply {
        arguments = ClusterFragmentArgs(item.identifier).toBundle()
    }

    override fun getPageTitle(position: Int): CharSequence = context.getString(
        R.string.deduplicator_cluster_x_label,
        "#$position"
    )

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            set.identifier == ClusterFragmentArgs.fromBundle(fragment.requireArguments()).identifier
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}