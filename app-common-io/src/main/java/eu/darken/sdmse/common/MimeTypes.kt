package eu.darken.sdmse.common

sealed class MimeTypes(val value: String) {

    data object Apk : MimeTypes("application/vnd.android.package-archive")
    data object Json : MimeTypes("application/json")
    data object Unknown : MimeTypes("application/octet-stream")
}
