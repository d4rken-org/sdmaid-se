package eu.darken.sdmse.common.serialization

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import eu.darken.sdmse.exclusion.core.types.Exclusion
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationAppModule {

    @Provides
    @Singleton
    fun json(
        @SerializationIO ioModule: SerializersModule = SerializationIOModule().serializersModule(),
    ): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        serializersModule = SerializersModule {
            include(ioModule)
            contextual(Exclusion::class, Exclusion.serializer())
            contextual(ArbiterCriterium::class, ArbiterCriterium.serializer())
        }
    }
}
