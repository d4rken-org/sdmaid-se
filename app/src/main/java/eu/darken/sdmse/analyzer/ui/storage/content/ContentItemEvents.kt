package eu.darken.sdmse.analyzer.ui.storage.content

import android.content.Intent
import eu.darken.sdmse.analyzer.core.content.ContentItem

sealed class ContentItemEvents {
    data class ShowNoAccessHint(val item: ContentItem) : ContentItemEvents()
    data class ExclusionsCreated(val count: Int) : ContentItemEvents()
    data class ContentDeleted(val count: Int, val freedSpace: Long) : ContentItemEvents()
    data class OpenContent(val intent: Intent) : ContentItemEvents()
}
