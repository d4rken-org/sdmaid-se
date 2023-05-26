package eu.darken.sdmse.common.coil

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.util.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Provider
import javax.inject.Singleton


@InstallIn(SingletonComponent::class)
@Module
class CoilModule {

    @Provides
    fun imageLoader(
        @ApplicationContext context: Context,
        generalSettings: GeneralSettings,
        appIconFetcherFactory: AppIconFetcher.Factory,
        pathPreviewFetcher: PathPreviewFetcher.Factory,
        dispatcherProvider: DispatcherProvider,
    ): ImageLoader = ImageLoader.Builder(context).apply {

        if (BuildConfigWrap.DEBUG) {
            val logger = object : Logger {
                override var level: Int = Log.VERBOSE
                override fun log(tag: String, priority: Int, message: String?, throwable: Throwable?) {
                    log("Coil:$tag", Logging.Priority.fromAndroid(priority)) { "$message ${throwable?.asLog()}" }
                }
            }
            logger(logger)
        }
        components {
            add(appIconFetcherFactory)
            add(VideoFrameDecoder.Factory())
            add(pathPreviewFetcher)
        }
        dispatcher(dispatcherProvider.Default)
    }.build()

    @Singleton
    @Provides
    fun imageLoaderFactory(imageLoaderSource: Provider<ImageLoader>): ImageLoaderFactory = ImageLoaderFactory {
        log(TAG) { "Preparing imageloader factory" }
        imageLoaderSource.get()
    }

    companion object {
        private val TAG = logTag("Coil", "Module")
    }
}
