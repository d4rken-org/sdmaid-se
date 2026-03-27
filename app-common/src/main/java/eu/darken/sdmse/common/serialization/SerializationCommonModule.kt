package eu.darken.sdmse.common.serialization

import android.net.Uri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okio.ByteString
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import javax.inject.Qualifier
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationCommonModule {

    @Provides
    @Singleton
    @SerializationCommon
    fun serializersModule(): SerializersModule = SerializersModule {
        contextual(Instant::class, InstantSerializer)
        contextual(Duration::class, DurationSerializer)
        contextual(UUID::class, UUIDSerializer)
        contextual(ByteString::class, ByteStringSerializer)
        contextual(File::class, FileSerializer)
        contextual(Uri::class, UriSerializer)
        contextual(OffsetDateTime::class, OffsetDateTimeSerializer)
        contextual(Regex::class, RegexSerializer)
        contextual(Locale::class, LocaleSerializer)
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
annotation class SerializationCommon
