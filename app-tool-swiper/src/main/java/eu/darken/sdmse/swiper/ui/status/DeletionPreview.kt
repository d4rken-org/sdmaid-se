package eu.darken.sdmse.swiper.ui.status

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem

/**
 * Aggregated preview of where the deletion reaches. Shown in the finalize confirmation dialog so users can see which
 * top-level folders are affected before committing, rather than only an aggregate count + size. Items that aren't
 * under any source path, or that sit directly in the source root, are intentionally omitted from the preview — the
 * dialog header still reports the full count and size.
 */
data class DeletionPreview(
    val buckets: List<DeletionBucket>,
    val moreFolders: Int,
) {
    data class DeletionBucket(
        val label: String,
        val count: Int,
        val size: Long,
    )

    companion object {
        const val MAX_BUCKETS = 5

        fun from(items: List<SwipeItem>, sourcePaths: Collection<APath>): DeletionPreview {
            val pending = items.filter {
                it.decision == SwipeDecision.DELETE || it.decision == SwipeDecision.DELETE_FAILED
            }
            if (pending.isEmpty()) return DeletionPreview(emptyList(), 0)

            val sourceSegments = sourcePaths.map { it.segments }
            val counts = mutableMapOf<String, Int>()
            val sizes = mutableMapOf<String, Long>()

            pending.forEach { item ->
                val itemSegs = item.lookup.segments
                val bestSource = sourceSegments
                    .filter { it.isAncestorOf(itemSegs) }
                    .maxByOrNull { it.size } ?: return@forEach
                val relative = itemSegs.drop(bestSource.size)
                if (relative.size < 2) return@forEach
                val key = relative.first()
                counts.merge(key, 1) { a, b -> a + b }
                sizes.merge(key, item.lookup.size) { a, b -> a + b }
            }

            val buckets = counts.keys
                .map { key -> DeletionBucket(label = key, count = counts.getValue(key), size = sizes.getValue(key)) }
                .sortedByDescending { it.size }

            val top = buckets.take(MAX_BUCKETS)
            val more = (buckets.size - MAX_BUCKETS).coerceAtLeast(0)
            return DeletionPreview(top, more)
        }
    }
}
