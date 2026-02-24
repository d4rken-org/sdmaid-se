package eu.darken.sdmse.analyzer.ui.storage.content

import android.content.Intent
import eu.darken.sdmse.analyzer.core.content.ContentItem

sealed class ContentItemEvents {
    data class ShowNoAccessHint(val item: ContentItem) : ContentItemEvents()
    data class ExclusionsCreated(val items: List<ContentItem>) : ContentItemEvents()
    data class ContentDeleted(val count: Int, val freedSpace: Long) : ContentItemEvents()
    data class OpenContent(val intent: Intent) : ContentItemEvents()
    data class SwiperSessionCreated(val sessionId: String, val itemCount: Int) : ContentItemEvents()
}
