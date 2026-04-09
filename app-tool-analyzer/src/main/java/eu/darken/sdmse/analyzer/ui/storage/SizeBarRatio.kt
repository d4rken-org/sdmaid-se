package eu.darken.sdmse.analyzer.ui.storage

/**
 * Computes the width ratio for a row's proportional size bar, scaled against
 * the largest sibling in the same listing. Returns null when a bar should not
 * be rendered (missing data or divide-by-zero).
 */
internal fun computeSizeBarRatio(itemSize: Long?, maxSiblingSize: Long?): Float? {
    if (itemSize == null || maxSiblingSize == null || maxSiblingSize <= 0L) return null
    return (itemSize.toFloat() / maxSiblingSize.toFloat()).coerceIn(0f, 1f)
}
