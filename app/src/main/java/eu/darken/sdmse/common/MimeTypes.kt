package eu.darken.sdmse.common

sealed class MimeTypes(val value: String) {

    data object Zip : MimeTypes("application/x-zip")
    data object Json : MimeTypes("application/json")
    data object Unknown : MimeTypes("application/octet-stream")
}