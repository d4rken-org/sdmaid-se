package eu.darken.sdmse.common.files.core.saf


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