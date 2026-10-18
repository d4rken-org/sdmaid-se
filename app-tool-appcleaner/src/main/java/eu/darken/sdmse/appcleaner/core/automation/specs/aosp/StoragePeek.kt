package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal const val STORAGE_LIST_ID = "com.android.settings:id/recycler_view"

/**
 * Whether a peek has scrolled the storage page away from the app header. While [away], a missing
 * header is our own doing and no sign of a stray press. [home] is where the header sat before.
 * [sawDrop] keeps a peek's verdict even if the run is cancelled while scrolling back.
 */
internal class StoragePeek {
    var away: Boolean = false
        private set
    var home: ACSNodeInfo.ScreenBounds? = null
        private set
    var sawDrop: Boolean = false

    fun leave(header: ACSNodeInfo.ScreenBounds) {
        home = header
        away = true
    }

    fun returned() {
        away = false
    }
}

internal sealed interface PeekResult {
    /** The page was scrolled back; [bytes] is the Cache row's value below the fold, null if unreadable. */
    data class Read(val shown: Boolean, val bytes: Long?) : PeekResult

    /** No scrollable storage list or no header to come back to: nothing was scrolled. */
    data object Unavailable : PeekResult

    /** The header didn't come back to where it was. */
    data object NotRestored : PeekResult
}

private fun ACSNodeInfo.findStorageList(): ACSNodeInfo? = crawl().map { it.node }
    .firstOrNull { it.viewIdResourceName == STORAGE_LIST_ID && it.isScrollable }

private fun ACSNodeInfo.findStorageHeader(): ACSNodeInfo? = crawl().map { it.node }
    .firstOrNull { it.viewIdResourceName == ENTITY_HEADER_CONTENT_ID }

internal fun ACSNodeInfo.showsStorageList(): Boolean = crawl().any { it.node.viewIdResourceName == STORAGE_LIST_ID }

/**
 * Scrolls the storage page forward until the Cache row shows, reads it, and scrolls back to the top.
 *
 * With large font and display sizes the Cache row sits below the fold (Android 17: only "App size" left
 * on screen). [isDrop] is judged before the way back, which runs even if the caller is cancelled,
 * bounded by [PEEK_RESTORE_TIMEOUT_MS].
 */
internal suspend fun StepContext.peekCacheRow(
    peek: StoragePeek,
    hasRow: (ACSNodeInfo) -> Boolean,
    readRow: suspend (ACSNodeInfo) -> Long?,
    isDrop: (Long?) -> Boolean = { false },
): PeekResult {
    val start = host.windowRoot()
    val header = start?.findStorageHeader()
    if (start?.findStorageList() == null || header == null) {
        log(tag, INFO) { "Peek: no scrollable storage page, not scrolling" }
        return PeekResult.Unavailable
    }
    peek.leave(header.getScreenBounds())
    var shown = false
    var bytes: Long? = null
    var restored = false
    try {
        for (scroll in 1..PEEK_MAX_SCROLLS) {
            val scrolled = host.windowRoot()?.findStorageList()?.performAction(ACSNodeInfo.ACTION_SCROLL_FORWARD) == true
            delay(PEEK_SETTLE_MS)
            val root = host.windowRoot()
            if (root != null && hasRow(root)) {
                shown = true
                bytes = readSettledRow(readRow)
                if (isDrop(bytes)) peek.sawDrop = true
                log(tag, INFO) { "Peek: Cache row after $scroll scroll(s): $bytes" }
                break
            }
            if (!scrolled) {
                log(tag, INFO) { "Peek: list end after $scroll scroll(s), no Cache row" }
                break
            }
        }
    } finally {
        restored = withContext(NonCancellable) {
            withTimeoutOrNull(PEEK_RESTORE_TIMEOUT_MS) { scrollBackToHeader(peek) } == true
        }
    }
    return if (restored) PeekResult.Read(shown, bytes) else PeekResult.NotRestored
}

// Settings fills the size rows asynchronously, so a row is only trusted once two reads agree.
internal suspend fun StepContext.readSettledRow(readRow: suspend (ACSNodeInfo) -> Long?): Long? {
    var last: Long? = null
    repeat(PEEK_ROW_READS) {
        val now = host.windowRoot()?.let { readRow(it) }
        if (now != null && now == last) return now
        last = now
        delay(PEEK_SETTLE_MS)
    }
    return null
}

/** Scrolls back until the header holds still where it was before the peek, twice in a row. */
internal suspend fun StepContext.scrollBackToHeader(peek: StoragePeek): Boolean {
    if (!peek.away) return true
    val home = peek.home ?: return false
    var atHome = false
    repeat(PEEK_MAX_SCROLLS + 4) {
        val bounds = host.windowRoot()?.findStorageHeader()?.getScreenBounds()
        if (bounds == home) {
            if (atHome) {
                log(tag, INFO) { "Peek: back at the top, header=$bounds" }
                peek.returned()
                return true
            }
            atHome = true
        } else {
            atHome = false
            host.windowRoot()?.findStorageList()?.performAction(ACSNodeInfo.ACTION_SCROLL_BACKWARD)
        }
        delay(PEEK_SETTLE_MS)
    }
    log(tag, WARN) { "Peek: the header didn't return to $home" }
    return false
}

private const val PEEK_MAX_SCROLLS = 3
private const val PEEK_SETTLE_MS = 300L
private const val PEEK_ROW_READS = 4
internal const val PEEK_RESTORE_TIMEOUT_MS = 2_000L

/** Longest a peek takes from its first scroll to the end of its row read. */
internal const val PEEK_READ_MS = (PEEK_MAX_SCROLLS + PEEK_ROW_READS) * PEEK_SETTLE_MS
