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
 * Emits the [ancestryKey] of every path that would be a strict ancestor of this one, e.g. LOCAL
 * `/a/b/c` emits `""`, `/a`, `/a/b` and `/`. Keys of different path types can collide, so keep them
 * apart per [APath.PathType]. Equivalence with `isAncestorOf` is pinned by `APathAncestorIndexTest`.
 */
inline fun APath.forEachAncestorKey(action: (String) -> Unit) {
    when (pathType) {
        APath.PathType.SAF -> {
            val safPath = this as SAFPath
            val segments = safPath.segments
            if (segments.isNotEmpty()) {
                val builder = StringBuilder().appendKeyComponent(safPath.treeRootUri.toString())
                action(builder.toString())
                for (i in 0 until segments.size - 1) {
                    builder.appendKeyComponent(segments[i])
                    action(builder.toString())
                }
            }
        }

        APath.PathType.LOCAL -> {
            val raw = path
            for (i in raw.indices) {
                if (raw[i] == File.separatorChar) action(raw.substring(0, i))
            }
            // The prefix walk above never emits the root itself.
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
