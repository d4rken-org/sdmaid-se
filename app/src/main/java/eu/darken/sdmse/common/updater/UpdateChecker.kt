package eu.darken.sdmse.common.updater

interface UpdateChecker {
    suspend fun currentChannel(): Channel

    suspend fun getLatest(channel: Channel): Update?

    suspend fun startUpdate(update: Update)

    suspend fun viewUpdate(update: Update)

    interface Update {
        val channel: Channel
        val versionName: String
    }

    enum class Channel {
        BETA,
        PROD
    }

}