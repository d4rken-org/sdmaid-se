package eu.darken.sdmse.common.files.saf

import android.content.UriPermission
import eu.darken.sdmse.common.files.Segments
import java.io.File


fun SAFPath.crumbsTo(child: SAFPath): Array<String> {
    require(this.treeRoot == child.treeRoot) { "roots don't match $treeRoot <> ${child.treeRoot}" }
    require(child.segments.size >= segments.size) { "${child.segments} isn't a child of $segments" }
    var lastMatchingIndex = 0
    for ((index, parentCrumb) in segments.withIndex()) {
        require(parentCrumb == child.segments[index]) {
            "Not parent and child: $segments - ${child.segments}"
        }
        lastMatchingIndex = index + 1
    }
    return child.segments.subList(lastMatchingIndex, child.segments.size).toTypedArray()
}

val SAFPath.isStorageRoot: Boolean
    get() = segments.isEmpty() && treeRootUri.pathSegments[1].split(":").filter { it.isNotEmpty() }.size == 1

data class PermissionMatch(
    val permission: UriPermission,
    val missingSegments: List<String>,
)

fun SAFPath.matchPermission(permissions: Collection<UriPermission>): PermissionMatch? {
    val targetSegments = mutableListOf<String>().apply {
        addAll(segments)
    }
    val missingSegments = mutableListOf<String>()

    val availablePermissions = permissions
        .filter { it.isReadPermission && it.isWritePermission }
        .map { uriPerm ->
            val segments = uriPerm.uri.path!!.split(":").last().split(File.separator)
            uriPerm to segments.filter { it.isNotEmpty() }
        }
        .sortedByDescending { it.second.size }

    while (true) {
        for ((perm, permsegments) in availablePermissions) {
            val samePrefix = pathUri.path!!.split(":").first() == perm.uri.path!!.split(":").first()
            if (samePrefix && permsegments == targetSegments) {
                return PermissionMatch(perm, missingSegments)
            }
        }

        targetSegments.removeLastOrNull()?.also { missingSegments.add(0, it) } ?: break
    }

    return null
}

fun SAFPath.isAncestorOf(child: SAFPath): Boolean {
    if (this.treeRoot != child.treeRoot) return false
    if (this.segments.size >= child.segments.size) return false
    if (this == child) return false
    return child.segments.take(this.segments.size) == this.segments
}

fun SAFPath.isParentOf(child: SAFPath): Boolean {
    if (this.treeRoot != child.treeRoot) return false
    if (this.segments.size + 1 != child.segments.size) return false
    if (this == child) return false
    return child.segments.dropLast(1) == this.segments
}

fun SAFPath.startsWith(prefix: SAFPath): Boolean {
    if (treeRoot != prefix.treeRoot) return false
    if (this == prefix) return true
    if (segments.size < prefix.segments.size) return false

    return when {
        prefix.segments.size == 1 -> {
            segments.first().startsWith(prefix.segments.first())
        }
        segments.size == prefix.segments.size -> {
            val match = prefix.segments.dropLast(1) == segments.dropLast(1)
            match && segments.last().startsWith(prefix.segments.last())
        }
        else -> {
            val match = prefix.segments.dropLast(1) == segments.dropLast(segments.size - prefix.segments.size + 1)
            match && segments[prefix.segments.size - 1].startsWith(prefix.segments.last())
        }
    }
}

fun SAFPath.removePrefix(prefix: SAFPath, overlap: Int = 0): Segments {
    if (!startsWith(prefix)) throw IllegalArgumentException("$prefix is not a prefix of $this")
    return segments.drop(prefix.segments.size - overlap)
}