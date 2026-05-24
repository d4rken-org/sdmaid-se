package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.common.storage.StorageId

sealed interface ContentCategory {
    val storageId: StorageId
    val spaceUsed: Long
    val groups: Collection<ContentGroup>
}

fun ContentCategory.ownsGroup(groupId: ContentGroup.Id): Boolean = groups.any { it.id == groupId }

/**
 * Whether content actions (delete, filter, Swiper) must be blocked for this category.
 *
 * System content is always read-only. Media content is read-only only in the degraded scan, i.e. when the app
 * inventory is unavailable and files can't be matched to their owners. Shared by the UI and the core delete guard so
 * the two definitions can't drift.
 */
val ContentCategory.isContentReadOnly: Boolean
    get() = when (this) {
        is SystemCategory -> true
        is MediaCategory -> isReadOnly
        is AppCategory -> false
    }
