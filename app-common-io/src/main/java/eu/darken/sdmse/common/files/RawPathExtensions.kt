package eu.darken.sdmse.common.files

import java.io.File


fun RawPath.crumbsTo(child: RawPath): Array<String> {
    val childPath = child.path
    val parentPath = this.path
    val pure = childPath.replaceFirst(parentPath, "")
    return pure.split(java.io.File.separatorChar)
        .filter { it.isNotEmpty() }
        .toTypedArray()
}


fun RawPath.isAncestorOf(child: RawPath): Boolean {
    val parentPath = this.asFile().absolutePath
    val childPath = child.asFile().absolutePath

    return childPath.startsWith(parentPath + File.separator)
}