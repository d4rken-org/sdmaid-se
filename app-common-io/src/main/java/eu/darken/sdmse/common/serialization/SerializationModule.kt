package eu.darken.sdmse.common.serialization

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.serialization.*
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationModule {

    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder().apply {
        add(InstantAdapter())
        add(UUIDAdapter())
        add(ByteStringAdapter())
        add(FileAdapter())
        add(UriAdapter())
        add(APath.MOSHI_FACTORY)
    }.build()
}
