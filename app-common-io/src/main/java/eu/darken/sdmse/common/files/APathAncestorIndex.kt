package eu.darken.sdmse.common.files

/**
 * Answers ancestry questions about a growing set of items in time proportional to the queried
 * path's depth instead of the number of items.
 *
 * Ancestry and exact matching use different keys on purpose: `SAFPath.path` omits the tree URI's
 * authority, so two paths in different trees can [matches] each other without either being an
 * ancestor of the other.
 */
class APathAncestorIndex<T : Any>(private val pathOf: (T) -> APath) {

    private val byAncestryKey = mutableMapOf<APath.PathType, MutableMap<String, MutableList<T>>>()
    private val byPath = mutableMapOf<APath.PathType, MutableSet<String>>()

    fun add(item: T) {
        val path = pathOf(item)
        byAncestryKey
            .getOrPut(path.pathType) { HashMap() }
            .getOrPut(path.ancestryKey()) { ArrayList(1) }
            .add(item)
        byPath.getOrPut(path.pathType) { HashSet() }.add(path.path)
    }

    /** Indexed items whose path is a strict ancestor of [path] ([isAncestorOf]). */
    fun ancestorsOf(path: APath): List<T> {
        val keys = byAncestryKey[path.pathType] ?: return emptyList()
        var result: MutableList<T>? = null
        path.forEachAncestorKey { key ->
            val hits = keys[key] ?: return@forEachAncestorKey
            (result ?: ArrayList<T>().also { result = it }).addAll(hits)
        }
        return result ?: emptyList()
    }

    fun hasAncestorOf(path: APath): Boolean {
        val keys = byAncestryKey[path.pathType] ?: return false
        path.forEachAncestorKey { key ->
            if (keys.containsKey(key)) return true
        }
        return false
    }

    /** True if an indexed item's path is a strict ancestor of [path] or [matches] it. */
    fun hasAncestorOrMatchOf(path: APath): Boolean {
        if (byPath[path.pathType]?.contains(path.path) == true) return true
        return hasAncestorOf(path)
    }
}
