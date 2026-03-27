package eu.darken.sdmse.swiper.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FileTypeCategory(val extensions: Set<String>) {
    @SerialName("IMAGES") IMAGES(
        setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif",
            "svg", "ico", "tiff", "tif", "raw", "cr2", "nef", "arw", "dng",
        )
    ),
    @SerialName("VIDEOS") VIDEOS(
        setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
            "3gp", "3g2", "ts", "mts", "m2ts", "vob",
        )
    ),
    @SerialName("AUDIO") AUDIO(
        setOf(
            "mp3", "flac", "aac", "ogg", "wav", "wma", "m4a", "opus",
            "alac", "aiff", "mid", "midi",
        )
    ),
    @SerialName("DOCUMENTS") DOCUMENTS(
        setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "rtf", "csv", "odt", "ods", "odp", "epub",
            "html", "htm", "xml", "json", "md",
        )
    ),
    @SerialName("ARCHIVES") ARCHIVES(
        setOf(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst",
            "apk", "jar", "cab", "iso", "dmg",
        )
    ),
    ;

    companion object {
        fun fromExtension(ext: String): FileTypeCategory? {
            val lower = ext.lowercase()
            return entries.firstOrNull { lower in it.extensions }
        }
    }
}

@Serializable
data class FileTypeFilter(
    val categories: Set<FileTypeCategory> = emptySet(),
    val customExtensions: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = categories.isEmpty() && customExtensions.isEmpty()

    fun matchesExtension(ext: String): Boolean {
        if (isEmpty) return true
        val lower = ext.lowercase()
        if (customExtensions.any { it.equals(lower, ignoreCase = true) }) return true
        val category = FileTypeCategory.fromExtension(lower) ?: return false
        return category in categories
    }

    companion object {
        val EMPTY = FileTypeFilter()

        fun parseCustomExtensions(input: String): Set<String> {
            return input.split(",", " ", "\n")
                .map { it.trim().removePrefix(".").lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }
}
