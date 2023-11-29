package eu.darken.sdmse.common

sealed class MimeTypes(val value: String) {

    data object Json : MimeTypes("application/json")
}