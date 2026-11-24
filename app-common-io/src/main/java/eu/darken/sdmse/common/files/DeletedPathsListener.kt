package eu.darken.sdmse.common.files

/**
 * Told about paths another tool deleted, so state cached from an earlier scan can drop them.
 * Must return quickly: it's called from inside the deleting tool's task.
 */
fun interface DeletedPathsListener {
    fun onPathsDeleted(paths: Set<APath>)
}
