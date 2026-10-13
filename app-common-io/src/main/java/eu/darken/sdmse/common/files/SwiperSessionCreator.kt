package eu.darken.sdmse.common.files

fun interface SwiperSessionCreator {
    suspend fun createSession(paths: Set<APath>): Result

    sealed interface Result {
        data class Created(val sessionId: String) : Result
        data object LimitReached : Result
    }
}
