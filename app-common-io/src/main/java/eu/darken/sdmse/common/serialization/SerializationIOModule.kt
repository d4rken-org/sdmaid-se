package eu.darken.sdmse.common.serialization

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.files.APath
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import javax.inject.Qualifier
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationIOModule {

    @Provides
    @Singleton
    @SerializationIO
    fun serializersModule(
        @SerializationCommon commonModule: SerializersModule = SerializationCommonModule().serializersModule(),
    ): SerializersModule = SerializersModule {
        include(commonModule)
        contextual(APath::class, APathSerializer)
    }

    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        serializersModule = serializersModule()
    }
}

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class SerializationIO
