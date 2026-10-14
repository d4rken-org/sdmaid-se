package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.files.saf.SAFPath
import java.io.File

/**
 * Appends one key component as `<UTF-16 length>:<value>`. Length-prefixing keeps the concatenation
 * injective no matter what the component contains, so the key needs no delimiter character.
 */
@PublishedApi
internal fun StringBuilder.appendKeyComponent(component: String): StringBuilder =
    append(component.length).append(':').append(component)

/**
 * The key a path is indexed under, matching what [forEachAncestorKey] emits for its descendants.
 */
fun APath.ancestryKey(): String = when (pathType) {
    APath.PathType.SAF -> (this as SAFPath).let { safPath ->
        val builder = StringBuilder().appendKeyComponent(safPath.treeRootUri.toString())
        safPath.segments.forEach { builder.appendKeyComponent(it) }
        builder.toString()
    }

    else -> path
}

/**
 * Emits the key of every path that would be a strict ancestor of this one, so that for two paths of
 * the same [APath.PathType], `a.isAncestorOf(b)` holds exactly when `b` emits `a.ancestryKey()`.
 * Keys of different path types can collide and must be kept apart by the caller.
 *
 * - **LOCAL**: `isAncestorOf` is a proper string prefix that ends on a separator boundary, plus an
 *   explicit branch that makes the root `/` an ancestor of everything below it. The ancestor keys are
 *   therefore the proper prefixes cut right before each separator, plus `/` itself.
 * - **RAW**: the ancestor test is the generic RAW branch of `APath.isAncestorOf`,
 *   `descendant.path.startsWith(this.path + "/")`. It is NOT `RawPath.isAncestorOf`, which
 *   absolutizes both sides. There is no root branch here either: `/` is not an ancestor of `/a`,
 *   because `/a` does not start with `//`.
 * - **SAF**: `isAncestorOf` is same-tree plus a proper `segments` prefix, so a SAF key is the tree URI
 *   plus a segment prefix. Those components are length-prefixed instead of joined by a delimiter:
 *   segment content is arbitrary (display names come verbatim from a DocumentsProvider cursor), so no
 *   character can be reserved as a separator, while a length-prefixed concatenation is injective for
 *   any content.
 */
inline fun APath.forEachAncestorKey(action: (String) -> Unit) {
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
