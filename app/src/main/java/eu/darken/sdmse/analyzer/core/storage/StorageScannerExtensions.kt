package eu.darken.sdmse.analyzer.core.storage

import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.walk
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
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


internal suspend fun APath.walkContentItem(gatewaySwitch: GatewaySwitch): ContentItem {
    log(TAG, VERBOSE) { "Walking content items for $this" }

    // What ever `this` is , the gatewaySwitch should make sure we end up with something usable
    val lookup = gatewaySwitch.lookup(this, type = GatewaySwitch.Type.AUTO)

    return if (lookup.fileType == FileType.DIRECTORY) {
        val children = try {
            lookup.walk(gatewaySwitch).map { ContentItem.fromLookup(it) }.toList()
        } catch (e: ReadException) {
            log(TAG, WARN) { "Failed to walk $this: ${e.asLog()}" }
            emptySet()
        }

        children.plus(ContentItem.fromLookup(lookup)).toNestedContent().single()
    } else {
        ContentItem.fromLookup(lookup)
    }
}

private val TAG = logTag("Analyzer", "Storage", "Scanner", "Extensions")
