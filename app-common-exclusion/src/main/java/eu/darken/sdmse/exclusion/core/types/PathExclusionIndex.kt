package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.saf.SAFPath
import java.io.File

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
 * - **LOCAL**: `matches` is `LocalPath.path` string equality. `isAncestorOf` is a proper string
 *   prefix that ends on a separator boundary, plus an explicit branch that makes the root `/` an
 *   ancestor of everything below it. The candidate's ancestor keys are therefore its proper
 *   prefixes cut right before each separator, plus `/` itself.
 * - **RAW**: `matches` is `path` string equality. The ancestor test is the generic RAW branch of
 *   `APath.isAncestorOf`, `descendant.path.startsWith(this.path + "/")`. It is NOT
 *   `RawPath.isAncestorOf`, which absolutizes both sides, so `PathExclusion(RawPath("a"))` does not
 *   match `RawPath("/cwd/a/b")` and neither does this index. There is no root branch here either:
 *   `/` is not an ancestor of `/a`, because `/a` does not start with `//`.
 * - **SAF**: `matches` is `SAFPath.path` string equality (that string already folds in
 *   `treeRootUri.pathSegments`, so the exact half needs no separate tree check). `isAncestorOf` is
 *   same-tree plus a proper `segments` prefix, so a SAF key is the tree URI plus a segment prefix.
 *   Those components are length-prefixed instead of joined by a delimiter: segment content is
 *   arbitrary (`SAFGateway` takes display names verbatim from a DocumentsProvider cursor), so no
 *   character can be reserved as a separator, while a length-prefixed concatenation is injective
 *   for any content.
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
            ancestors.getOrPut(path.pathType) { mutableSetOf() }.add(path.exclusionKey())
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
        keys[candidate.pathType]?.contains(candidate.exclusionKey()) == true
}

/**
 * Appends one key component as `<UTF-16 length>:<value>`. Length-prefixing keeps the concatenation
 * injective no matter what the component contains, so the key needs no delimiter character.
 */
private fun StringBuilder.appendKeyComponent(component: String): StringBuilder =
    append(component.length).append(':').append(component)

/**
 * The key a path is indexed under, matching what [forEachAncestorKey] emits for its descendants.
 */
internal fun APath.exclusionKey(): String = when (pathType) {
    APath.PathType.SAF -> (this as SAFPath).let { safPath ->
        val builder = StringBuilder().appendKeyComponent(safPath.treeRootUri.toString())
        safPath.segments.forEach { builder.appendKeyComponent(it) }
        builder.toString()
    }

    else -> path
}

/**
 * Emits the key of every path that would be a strict ancestor of this one.
 */
internal inline fun APath.forEachAncestorKey(action: (String) -> Unit) {
    when (pathType) {
        APath.PathType.SAF -> {
            val safPath = this as SAFPath
            val builder = StringBuilder().appendKeyComponent(safPath.treeRootUri.toString())
            action(builder.toString())
            for (i in 0 until safPath.segments.size - 1) {
                builder.appendKeyComponent(safPath.segments[i])
                action(builder.toString())
            }
        }

        APath.PathType.LOCAL -> {
            val raw = path
            for (i in raw.indices) {
                if (raw[i] == File.separatorChar) action(raw.substring(0, i))
            }
            // LocalPath.isAncestorOf treats the root as an ancestor of everything below it, and the
            // prefix walk above never produces the root on its own.
            if (raw.length > 1 && raw[0] == File.separatorChar) action(File.separator)
        }

        APath.PathType.RAW -> {
            val raw = path
            for (i in raw.indices) {
                if (raw[i] == '/') action(raw.substring(0, i))
            }
        }
    }
}
