package eu.darken.sdmse.main.core.motd

import com.squareup.moshi.Moshi
import dagger.Reusable
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.Locale
import javax.inject.Inject

@Reusable
class MotdEndpoint @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val baseHttpClient: OkHttpClient,
    private val baseMoshi: Moshi,
) {

    var endpointUrlOverride: String? = null

    private val api: MotdApi by lazy {
        Retrofit.Builder().apply {
            baseUrl(endpointUrlOverride ?: "https://api.github.com")
            client(baseHttpClient)
            addConverterFactory(ScalarsConverterFactory.create())
            addConverterFactory(MoshiConverterFactory.create(baseMoshi).asLenient())
        }.build().create(MotdApi::class.java)
    }

    suspend fun getMotd(locale: Locale): MotdState? {
        log(TAG, VERBOSE) { "getMotd(locale=$locale)..." }
        return try {
            getMotd(BuildConfigWrap.FLAVOR, BuildConfigWrap.BUILD_TYPE, locale)
        } catch (e: HttpException) {
            log(TAG, ERROR) { "getMotd($locale) error: ${e.asLog()}" }
            if (e.code() == 404) null else throw e
        }
    }

    private suspend fun getMotd(
        flavor: BuildConfigWrap.Flavor,
        buildType: BuildConfigWrap.BuildType,
        locale: Locale,
    ): MotdState? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "getMotd($flavor, $buildType, $locale)..." }

        val branch = when (BuildConfigWrap.BUILD_TYPE) {
            BuildConfigWrap.BuildType.DEV -> "motd"
            BuildConfigWrap.BuildType.BETA, BuildConfigWrap.BuildType.RELEASE -> "main"
        }

        val flavorRaw = when (BuildConfigWrap.FLAVOR) {
            BuildConfigWrap.Flavor.FOSS -> "foss"
            BuildConfigWrap.Flavor.GPLAY -> "gplay"
            BuildConfigWrap.Flavor.NONE -> throw IllegalArgumentException("flavor can't be NONE")
        }

        val buildRaw = when (BuildConfigWrap.BUILD_TYPE) {
            BuildConfigWrap.BuildType.DEV -> "dev"
            BuildConfigWrap.BuildType.BETA -> "beta"
            BuildConfigWrap.BuildType.RELEASE -> "release"
        }

        val motds = api.listMotds(
            path = "motd/$flavorRaw/$buildRaw",
            branch = branch
        ).also { log(TAG, VERBOSE) { "getMotd($branch, $flavorRaw)... $it" } }

        val filteredMotds = motds.filter { it.type == "file" }

        var usedLocale = locale
        var localizedMotd = filteredMotds.singleOrNull { it.name.endsWith("-${locale.language}.json") }

        if (localizedMotd == null) {
            localizedMotd = filteredMotds.singleOrNull { it.name.endsWith("-en.json") }
            usedLocale = Locale.ENGLISH
        }
        if (localizedMotd == null) {
            localizedMotd = filteredMotds.singleOrNull { it.name.endsWith(".json") }
            usedLocale = Locale.ENGLISH
        }

        val motd = localizedMotd?.downloadUrl?.let { api.getMotd(it) }

        return@withContext motd?.let {
            MotdState(
                motd = it,
                locale = usedLocale,
            )
        }
    }

    companion object {
        private val TAG = logTag("Motd", "Endpoint")
    }
}