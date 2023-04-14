package eu.darken.sdmse.common.updater

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
class GplayUpdateChecker @Inject constructor(

) : UpdateChecker {
    override suspend fun currentChannel(): UpdateChecker.Channel {
        return UpdateChecker.Channel.PROD
    }

    override suspend fun getLatest(channel: UpdateChecker.Channel): UpdateChecker.Update? {
        return null
    }

    override suspend fun startUpdate(update: UpdateChecker.Update) {
        log(TAG) { "startUpdate($update)" }
        // NOOP
    }

    override suspend fun viewUpdate(update: UpdateChecker.Update) {
        log(TAG) { "viewUpdate($update)" }
        // NOOP
    }

    override suspend fun dismissUpdate(update: UpdateChecker.Update) {
        log(TAG) { "dismissUpdate($update)" }
        // NOOP
    }

    override suspend fun isDismissed(update: UpdateChecker.Update): Boolean {
        log(TAG) { "isDismissed($update)" }
        // NOOP
        return true
    }

    companion object {
        private val TAG = logTag("Updater", "Checker", "Gplay")
    }
}