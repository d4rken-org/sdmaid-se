package eu.darken.sdmse.analyzer.core.storage

import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.files.Segments
import java.util.LinkedList


internal fun Collection<ContentItem>.toNestedContent(): Collection<ContentItem> {
    val workList = this.sortedBy { it.path.segments.size }.reversed().toMutableList()

    val topLevel = mutableListOf<ContentItem>()

    val parentIndexMap = mutableMapOf<Segments, Int>()

    for (item in workList) {
        val childSegs = item.path.segments
        if (childSegs.isEmpty() || childSegs.singleOrNull() == "") {
            topLevel.add(item)
            continue
        }

        val parentSegs = childSegs.subList(0, childSegs.size - 1)

        val parentIndex = parentIndexMap[parentSegs]
            ?: workList.indexOfFirst {
                val segs = it.path.segments
                if (parentSegs.isEmpty()) {
                    segs.isEmpty() || segs.singleOrNull() == ""
                } else {
                    segs == parentSegs
                }
            }.also { parentIndexMap[parentSegs] = it }

        if (parentIndex != -1) {
            val parent = workList[parentIndex]

            val updatedParent = parent.copy(
                children = parent.children.plus(item)
            )
            workList[parentIndex] = updatedParent
        } else {
            topLevel.add(item)
        }
    }

    return topLevel
}

internal fun Collection<ContentItem>.toFlatContent(): Collection<ContentItem> =
    this.map { it.toFlatContent() }.flatten()

internal fun ContentItem.toFlatContent(): Collection<ContentItem> {
    val result = mutableListOf<ContentItem>()

    result.add(this.copy(children = emptySet()))

    children
        .map { it.toFlatContent() }
        .flatten()
        .let { result.addAll(it) }

    return result
}


internal fun Collection<ContentItem>.findContent(filter: (ContentItem) -> Boolean): ContentItem? {
    val queue = LinkedList(this)

    while (!queue.isEmpty()) {
        val item = queue.removeFirst()
        if (filter(item)) return item
        queue.addAll(item.children)
    }

    return null
}

