package eu.darken.sdmse.common.updater

import com.squareup.moshi.Moshi
import dagger.Reusable
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Inject

@Reusable
class GithubReleaseCheck @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val baseHttpClient: OkHttpClient,
    private val baseMoshi: Moshi,
) {

    private val api: GithubApi by lazy {
        Retrofit.Builder().apply {
            baseUrl("https://api.github.com")
            client(baseHttpClient)
            addConverterFactory(ScalarsConverterFactory.create())
            addConverterFactory(MoshiConverterFactory.create(baseMoshi).asLenient())
        }.build().create(GithubApi::class.java)
    }

    suspend fun latestRelease(
        owner: String,
        repo: String
    ): GithubApi.ReleaseInfo? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "latestRelease(owner=$owner, repo=$repo)..." }
        return@withContext try {
            api.latestRelease(owner, repo).also {
                log(TAG) { "latestRelease(owner=$owner, repo=$repo) is $it" }
            }
        } catch (e: HttpException) {
            if (e.code() == 404) {
                log(TAG, WARN) { "No release available." }
                return@withContext null
            }
            throw e
        }
    }

    suspend fun allReleases(
        owner: String,
        repo: String
    ): List<GithubApi.ReleaseInfo> = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "allReleases(owner=$owner, repo=$repo)..." }
        return@withContext api.allReleases(owner, repo).also {
            log(TAG) { "allReleases(owner=$owner, repo=$repo) is ${it.size} releases" }
        }
    }

    companion object {
        private val TAG = logTag("Updater", "Github", "ReleaseCheck")
    }
}