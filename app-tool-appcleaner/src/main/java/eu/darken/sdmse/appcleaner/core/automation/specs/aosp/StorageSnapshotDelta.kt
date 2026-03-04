package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

internal data class StorageSnapshot(val values: List<ParsedSize>) {
    internal data class ParsedSize(val bytes: Long?, val rawText: String)
}

internal enum class DeltaResult { SUCCESS, SKIP_SUCCESS, NO_CHANGE, INCONCLUSIVE }

internal fun compareSnapshots(pre: StorageSnapshot, post: StorageSnapshot): DeltaResult {
    if (pre.values.isEmpty() || post.values.isEmpty()) return DeltaResult.INCONCLUSIVE
    if (pre.values.size != post.values.size) return DeltaResult.INCONCLUSIVE

    val pairs = pre.values.zip(post.values).mapNotNull { (a, b) ->
        if (a.bytes != null && b.bytes != null) a.bytes to b.bytes else null
    }
    if (pairs.isEmpty()) return DeltaResult.INCONCLUSIVE

    if (pairs.any { (before, after) -> after < before }) return DeltaResult.SUCCESS

    if (pre.values.any { it.bytes == 0L }) return DeltaResult.SKIP_SUCCESS

    return DeltaResult.NO_CHANGE
}
