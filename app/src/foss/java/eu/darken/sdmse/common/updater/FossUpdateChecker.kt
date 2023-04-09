package eu.darken.sdmse.common.updater

import dagger.Reusable
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
class FossUpdateChecker @Inject constructor(
    private val checker: GithubReleaseCheck,
    private val webpageTool: WebpageTool,
) : UpdateChecker {

    override suspend fun currentChannel(): UpdateChecker.Channel = when (BuildConfigWrap.BUILD_TYPE) {
        BuildConfigWrap.BuildType.RELEASE -> UpdateChecker.Channel.PROD
        BuildConfigWrap.BuildType.BETA -> UpdateChecker.Channel.BETA
        BuildConfigWrap.BuildType.DEV -> UpdateChecker.Channel.BETA
    }

    override suspend fun getLatest(channel: UpdateChecker.Channel): UpdateChecker.Update? {
        val release = try {
            when (channel) {
                UpdateChecker.Channel.BETA -> checker.allReleases(OWNER, REPO).firstOrNull { it.isPreRelease }
                UpdateChecker.Channel.PROD -> checker.latestRelease(OWNER, REPO)
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "getLatest($channel) failed: ${e.asLog()}" }
            null
        }
        log(TAG, INFO) { "getLatest($channel) is $release" }

        if (release == null) return null

        return Update(
            channel = channel,
            versionName = release.tagName,
            changelogLink = release.htmlUrl,
            downloadLink = release.assets.singleOrNull { it.name.endsWith(".apk") }?.downloadUrl,
        )
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

    data class Update(
        override val channel: UpdateChecker.Channel,
        override val versionName: String,
        val changelogLink: String,
        val downloadLink: String?,
    ) : UpdateChecker.Update

    companion object {
        private const val OWNER = "d4rken-org"
        private const val REPO = "sdmaid-se"

        private val TAG = logTag("Updater", "Checker", "FOSS")
    }
}