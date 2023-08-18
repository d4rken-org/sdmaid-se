package eu.darken.sdmse.common

sealed class MimeTypes(val value: String) {

    object Json : MimeTypes("application/json")
}