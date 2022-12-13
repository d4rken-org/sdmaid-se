package eu.darken.sdmse.common.files.core.saf

import android.content.UriPermission
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
    get() = segments.isEmpty() && treeRoot.pathSegments[1].split(":").filter { it.isNotEmpty() }.size == 1

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