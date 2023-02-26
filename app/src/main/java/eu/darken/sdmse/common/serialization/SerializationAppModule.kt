package eu.darken.sdmse.common.serialization

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.serialization.*
import eu.darken.sdmse.exclusion.core.types.Exclusion
import javax.inject.Qualifier
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationAppModule {

    @Provides
    @Singleton
    fun moshi(
        @SerializationIO moshiIO: Moshi = SerializationIOModule().moshi()
    ): Moshi = moshiIO.newBuilder().apply {
        add(Exclusion.MOSHI_FACTORY)
    }.build()
}

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class SerializationIO
