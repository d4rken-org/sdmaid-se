package eu.darken.sdmse.common.updater

import dagger.Reusable
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Reusable
class FossUpdateChecker @Inject constructor(
    private val checker: GithubReleaseCheck,
    private val webpageTool: WebpageTool,
    private val settings: FossUpdateSettings,
) : UpdateChecker {

    override suspend fun currentChannel(): UpdateChecker.Channel = when (BuildConfigWrap.BUILD_TYPE) {
        BuildConfigWrap.BuildType.RELEASE -> UpdateChecker.Channel.PROD
        BuildConfigWrap.BuildType.BETA -> UpdateChecker.Channel.BETA
        BuildConfigWrap.BuildType.DEV -> UpdateChecker.Channel.BETA
    }

    override suspend fun getLatest(channel: UpdateChecker.Channel): UpdateChecker.Update? {
        log(TAG) { "getLatest($channel) checking..." }

        val release: GithubApi.ReleaseInfo? = try {
            if (Duration.between(settings.lastReleaseCheck.value(), Instant.now()) < UPDATE_CHECK_INTERVAL) {
                log(TAG) { "Using cached release data" }
                when (channel) {
                    UpdateChecker.Channel.BETA -> settings.lastReleaseBeta.value()
                    UpdateChecker.Channel.PROD -> settings.lastReleaseProd.value()
                }
            } else {
                log(TAG) { "Fetching new release data" }
                when (channel) {
                    UpdateChecker.Channel.BETA -> checker.allReleases(OWNER, REPO).firstOrNull { it.isPreRelease }
                    UpdateChecker.Channel.PROD -> checker.latestRelease(OWNER, REPO)
                }.also {
                    log(TAG, INFO) { "getLatest($channel) new data is $it" }
                    settings.lastReleaseCheck.value(Instant.now())
                    when (channel) {
                        UpdateChecker.Channel.BETA -> settings.lastReleaseBeta.value(it)
                        UpdateChecker.Channel.PROD -> settings.lastReleaseProd.value(it)
                    }
                }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "getLatest($channel) failed: ${e.asLog()}" }
            null
        }

        log(TAG, INFO) { "getLatest($channel) is ${release?.tagName}" }

        val update = release?.let { rel ->
            Update(
                channel = channel,
                versionName = rel.tagName,
                changelogLink = rel.htmlUrl,
                downloadLink = rel.assets.singleOrNull { it.name.endsWith(".apk") }?.downloadUrl,
            )
        }

        return update
    }

    override suspend fun startUpdate(update: UpdateChecker.Update) {
        log(TAG, INFO) { "startUpdate($update)" }
        update as Update
        if (update.downloadLink != null) {
            webpageTool.open(update.downloadLink)
        } else {
            log(TAG, WARN) { "No download link available for $update" }
        }
    }

    override suspend fun viewUpdate(update: UpdateChecker.Update) {
        log(TAG, INFO) { "viewUpdate($update)" }
        update as Update
        webpageTool.open(update.changelogLink)
    }

    override suspend fun dismissUpdate(update: UpdateChecker.Update) {
        log(TAG, INFO) { "dismissUpdate($update)" }
        update as Update
        settings.dismiss(update)
    }

    override suspend fun isDismissed(update: UpdateChecker.Update): Boolean {
        update as Update
        return settings.isDismissed(update)
    }

    data class Update(
        override val channel: UpdateChecker.Channel,
        override val versionName: String,
        val changelogLink: String,
        val downloadLink: String?,
    ) : UpdateChecker.Update

    companion object {
        private val UPDATE_CHECK_INTERVAL = Duration.ofHours(6)
        private const val OWNER = "d4rken-org"
        private const val REPO = "sdmaid-se"

        private val TAG = logTag("Updater", "Checker", "FOSS")
    }
}