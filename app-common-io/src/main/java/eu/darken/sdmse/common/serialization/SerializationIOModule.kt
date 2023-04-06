package eu.darken.sdmse.common.serialization

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.*
import javax.inject.Qualifier
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationIOModule {

    @Provides
    @Singleton
    @SerializationIO
    fun moshi(
        @SerializationCommon moshiCommon: Moshi = SerializationCommonModule().moshi()
    ): Moshi = moshiCommon.newBuilder().apply {
        add(APath.MOSHI_FACTORY)
    }.build()
}

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class SerializationIO
