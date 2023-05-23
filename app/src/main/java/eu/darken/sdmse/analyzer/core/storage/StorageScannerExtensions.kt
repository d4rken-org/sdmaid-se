package eu.darken.sdmse.analyzer.core.storage

import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.files.isParentOf

fun Collection<ContentItem>.toNesting(): Collection<ContentItem> {
    val workList = this.sortedByDescending { it.path.segments.size }.toMutableList()

    val topLevel = mutableListOf<ContentItem>()

    for (item in workList) {
        val parent = workList.singleOrNull { it.path.isParentOf(item.path) }
        if (parent != null) {
            val parentIndex = workList.indexOf(parent)
            val updatedParent = parent.copy(
                children = (parent.children ?: emptySet()).plus(item)
            )
            workList[parentIndex] = updatedParent
        } else {
            topLevel.add(item)
        }
    }

    return topLevel
}

