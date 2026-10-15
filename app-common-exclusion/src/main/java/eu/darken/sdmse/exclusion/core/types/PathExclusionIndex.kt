package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.ancestryKey
import eu.darken.sdmse.common.files.forEachAncestorKey

/**
 * Lookup structure that answers "does any of these exclusions match this path?" in time proportional
 * to the path's own depth instead of the number of exclusions.
 *
 * Motivation: a bulk exclude can create tens of thousands of [PathExclusion]s. Testing every
 * candidate against every exclusion is quadratic and, because [Exclusion.Path.match] is a suspend
 * function, it is a very expensive kind of quadratic.
 *
 * ## Why the index is equivalent to a linear scan over [PathExclusion]s
 * [PathExclusion.match] is `candidate.matches(path) || path.isAncestorOf(candidate)`. Both halves
 * are decidable from keys that the candidate alone can produce, so the exclusions can be hashed:
 * - `matches` is string equality on `path` for every path type. For SAF that string already folds in
 *   `treeRootUri.pathSegments`, so the exact half needs no separate tree check.
 * - `isAncestorOf` is decided by [ancestryKey] and [forEachAncestorKey], whose equivalence with
 *   `isAncestorOf` is pinned by `APathAncestorIndexTest` (app-common-io). For RAW they follow the
 *   generic `APath.isAncestorOf` branch, so `PathExclusion(RawPath("a"))` does not match
 *   `RawPath("/cwd/a/b")`.
 *
 * Exclusions that are not [PathExclusion]s (today: [SegmentExclusion]) cannot be keyed and stay on
 * the linear path.
 *
 * ## Documented behaviour change
 * The index always consults the [PathExclusion]s before the non-indexable exclusions, regardless of
 * collection order. The previous `any { it.match(candidate) }` was order-dependent in one observable
 * way: `RawPath.segments` throws `NotImplementedError`, so a [SegmentExclusion] evaluated before a
 * matching RAW [PathExclusion] threw, while the same set in the other order did not. Evaluation
 * order was never a defined contract (it already varied with collection order), so the index turns
 * an order-dependent latent crash into a consistent non-crash.
 */
class PathExclusionIndex(exclusions: Collection<Exclusion.Path>) {

    private val exactKeys: Map<APath.PathType, Set<String>>
    private val ancestorKeys: Map<APath.PathType, Set<String>>
    private val others: List<Exclusion.Path>

    init {
        val exact = mutableMapOf<APath.PathType, MutableSet<String>>()
        val ancestors = mutableMapOf<APath.PathType, MutableSet<String>>()
        val rest = mutableListOf<Exclusion.Path>()

        exclusions.forEach { exclusion ->
            if (exclusion !is PathExclusion) {
                rest.add(exclusion)
                return@forEach
            }
            val path = exclusion.path
            exact.getOrPut(path.pathType) { mutableSetOf() }.add(path.path)
            ancestors.getOrPut(path.pathType) { mutableSetOf() }.add(path.ancestryKey())
        }

        exactKeys = exact
        ancestorKeys = ancestors
        others = rest
    }

    suspend fun matches(candidate: APath): Boolean {
        val key = matchesIndexed(candidate)
        if (key != null) {
            log(TAG, VERBOSE) { "Exclusion match: $candidate <- $key" }
            return true
        }
        return others.any { it.match(candidate) }
    }

    /**
     * The key that matched, or `null` if none did. The key is returned instead of a boolean so the
     * caller can log which exclusion was responsible.
     */
    private fun matchesIndexed(candidate: APath): String? {
        val type = candidate.pathType
        if (exactKeys[type]?.contains(candidate.path) == true) return candidate.path

        val keys = ancestorKeys[type] ?: return null
        var hit: String? = null
        candidate.forEachAncestorKey { key ->
            if (hit == null && keys.contains(key)) hit = key
        }
        return hit
    }

    companion object {
        private val TAG = logTag("Exclusion", "Path", "Index")
    }
}

/**
 * Reverse of [PathExclusionIndex]: answers "is this path a strict ancestor of any of the indexed
 * paths?". Built from the already excluded paths so that a survivor can be checked in O(depth)
 * instead of being compared against every excluded path.
 */
internal class StrictAncestorIndex(paths: Collection<APath>) {

    private val keys: Map<APath.PathType, Set<String>>

    init {
        val collected = mutableMapOf<APath.PathType, MutableSet<String>>()
        paths.forEach { path ->
            val bucket = collected.getOrPut(path.pathType) { mutableSetOf() }
            path.forEachAncestorKey { bucket.add(it) }
        }
        keys = collected
    }

    fun isStrictAncestorOfIndexed(candidate: APath): Boolean =
        keys[candidate.pathType]?.contains(candidate.ancestryKey()) == true
}
