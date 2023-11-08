package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import javax.inject.Inject

class CommonFilesCheck @Inject constructor(
    private val mimeTypeTool: MimeTypeTool,
) {

    suspend fun isCommon(lookup: APathLookup<*>): Boolean {
        val mimeType = mimeTypeTool.determineMimeType(lookup)
        if (Bugs.isTrace) log(TAG, VERBOSE) { "$mimeType <- ${lookup.path}" }
        return COMMON_TYPES.contains(mimeType)
    }

    suspend fun isImage(lookup: APathLookup<*>): Boolean {
        val mimeType = mimeTypeTool.determineMimeType(lookup)
        if (Bugs.isTrace) log(TAG, VERBOSE) { "$mimeType <- ${lookup.path}" }
        return IMAGES.contains(mimeType)
    }

    companion object {
        private val IMAGES = setOf(
            "image/x-ms-bmp",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml",
        )

        private val VIDEOS = setOf(
            "video/mp4",
            "video/webm",
            "video/ogg",
            "video/x-msvideo",
            "video/mpeg"
        )

        private val AUDIO = setOf(
            "audio/mpeg",
            "audio/ogg",
            "audio/wav",
            "audio/webm",
            "audio/aac",
            "audio/x-wav",
            "audio/x-aiff"
        )
        private val ARCHIVES = setOf(
            "application/zip",
            "application/x-rar-compressed",
            "application/x-tar",
            "application/gzip",
            "application/x-7z-compressed"
        )

        private val DOCUMENTS = setOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/html",
            "text/csv",
            "text/rtf"
        )
        private val COMMON_TYPES = IMAGES + VIDEOS + AUDIO + ARCHIVES + DOCUMENTS
        private val TAG = logTag("Deduplicator", "CommonFilesCheck")
    }
}