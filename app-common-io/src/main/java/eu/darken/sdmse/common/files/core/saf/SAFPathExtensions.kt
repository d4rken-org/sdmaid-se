package eu.darken.sdmse.common.files.core.saf

import android.content.UriPermission
import eu.darken.sdmse.common.dropLastColon
import java.io.File


fun SAFPath.crumbsTo(child: SAFPath): Array<String> {
    require(this.treeRoot == child.treeRoot) { "roots don't match $treeRoot <> ${child.treeRoot}" }
    require(child.crumbs.size >= crumbs.size) { "${child.crumbs} isn't a child of $crumbs" }
    var lastMatchingIndex = 0
    for ((index, parentCrumb) in crumbs.withIndex()) {
        require(parentCrumb == child.crumbs[index]) {
            "Not parent and child: $crumbs - ${child.crumbs}"
        }
        lastMatchingIndex = index + 1
    }
    return child.crumbs.subList(lastMatchingIndex, child.crumbs.size).toTypedArray()
}

val SAFPath.isStorageRoot: Boolean
    get() = crumbs.isEmpty() && treeRoot.pathSegments[1].split(":").filter { it.isNotEmpty() }.size == 1

data class PermissionMatch(
    val permission: UriPermission,
    val missingSegments: List<String>,
)

fun SAFPath.matchPermission(permissions: Collection<UriPermission>): PermissionMatch? {
    val targetSegments = mutableListOf<String>().apply {
        addAll(crumbs)
    }
    val missingSegments = mutableListOf<String>()

    val availablePermissions = permissions
        .filter { it.isReadPermission && it.isWritePermission }
        .map { uriPerm ->
            val segments = uriPerm.uri.path!!.split(":").last().split(File.separator)
            uriPerm to segments.filter { it.isNotEmpty() }
        }
        .sortedByDescending { it.second.size }

    do {
        for ((perm, permCrumbs) in availablePermissions) {
            if (permCrumbs == targetSegments && perm.uri.dropLastColon() == pathUri) {
                return PermissionMatch(perm, missingSegments)
            }
        }
        targetSegments.removeLastOrNull()?.let { missingSegments.add(0, it) }
    } while (targetSegments.isNotEmpty())

    return null
}