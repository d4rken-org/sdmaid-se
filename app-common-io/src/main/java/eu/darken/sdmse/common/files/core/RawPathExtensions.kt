package eu.darken.sdmse.common.files.core


fun RawPath.crumbsTo(child: RawPath): Array<String> {
    val childPath = child.path
    val parentPath = this.path
    val pure = childPath.replaceFirst(parentPath, "")
    return pure.split(java.io.File.separatorChar)
        .filter { it.isNotEmpty() }
        .toTypedArray()
}