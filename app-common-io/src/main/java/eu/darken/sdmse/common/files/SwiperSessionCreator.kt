package eu.darken.sdmse.common.files

fun interface SwiperSessionCreator {
    suspend fun createSession(paths: Set<APath>): String
}
