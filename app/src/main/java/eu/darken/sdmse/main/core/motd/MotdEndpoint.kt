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

    private val api: MotdApi by lazy {
        Retrofit.Builder().apply {
            baseUrl("https://api.github.com")
            client(baseHttpClient)
            addConverterFactory(ScalarsConverterFactory.create())
            addConverterFactory(MoshiConverterFactory.create(baseMoshi).asLenient())
        }.build().create(MotdApi::class.java)
    }

    suspend fun getMotd(locale: Locale): Motd? {
        log(TAG, VERBOSE) { "getMotd(locale=$locale)..." }
        return try {
            getMotd(BuildConfigWrap.FLAVOR, BuildConfigWrap.BUILD_TYPE, locale)
        } catch (e: Exception) {
            log(TAG, ERROR) { "getMotd($locale) error: ${e.asLog()}" }
            throw e
        }
    }

    private suspend fun getMotd(
        flavor: BuildConfigWrap.Flavor,
        buildType: BuildConfigWrap.BuildType,
        locale: Locale,
    ): Motd? = withContext(dispatcherProvider.IO) {
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

        val languageRaw = locale.language

        var localizedMotd = filteredMotds.singleOrNull { it.name.endsWith("-$languageRaw.json") }
        if (localizedMotd == null) localizedMotd = filteredMotds.singleOrNull { it.name.endsWith("-en.json") }
        if (localizedMotd == null) localizedMotd = filteredMotds.singleOrNull { it.name.endsWith(".json") }

        return@withContext localizedMotd?.downloadUrl?.let { api.getMotd(it) }
    }

    companion object {
        private val TAG = logTag("Motd", "Endpoint")
    }
}