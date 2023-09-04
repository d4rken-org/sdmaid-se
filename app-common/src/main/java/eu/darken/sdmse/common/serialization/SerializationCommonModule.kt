package eu.darken.sdmse.common.serialization

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.serialization.*
import javax.inject.Qualifier
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationCommonModule {

    @Provides
    @Singleton
    @SerializationCommon
    fun moshi(): Moshi = Moshi.Builder().apply {
        add(InstantAdapter())
        add(DurationAdapter())
        add(UUIDAdapter())
        add(ByteStringAdapter())
        add(FileAdapter())
        add(UriAdapter())
        add(OffsetDateTimeAdapter())
        add(RegexAdapter())
        add(LocaleAdapter())
    }.build()
}

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class SerializationCommon
