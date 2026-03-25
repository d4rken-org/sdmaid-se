package eu.darken.sdmse.deduplicator.ui.details

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import eu.darken.sdmse.common.getSpanCount
import eu.darken.sdmse.common.uix.DetailsPagerAdapter3
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterFragment
import eu.darken.sdmse.deduplicator.ui.details.cluster.ClusterViewModel

class DeduplicatorDetailsPagerAdapter(
    activity: FragmentActivity,
    fm: FragmentManager,
) : DetailsPagerAdapter3<Duplicate.Cluster>(activity, fm) {

    override fun getPageWidth(position: Int): Float = 1f / context.getSpanCount()

    override fun onCreateFragment(item: Duplicate.Cluster): Fragment = ClusterFragment().apply {
        arguments = ClusterViewModel.Args(identifier = item.identifier).toBundle()
    }

    override fun getPageTitle(position: Int): CharSequence = context.getString(
        eu.darken.sdmse.deduplicator.R.string.deduplicator_cluster_x_label,
        "#$position"
    )

    override fun getItemPosition(obj: Any): Int = data
        .firstOrNull { set ->
            val fragment = obj as Fragment
            set.identifier == @Suppress("DEPRECATION") fragment.requireArguments().getParcelable<Duplicate.Cluster.Id>("identifier")
        }
        ?.let { data.indexOf(it) }
        ?: POSITION_NONE
}