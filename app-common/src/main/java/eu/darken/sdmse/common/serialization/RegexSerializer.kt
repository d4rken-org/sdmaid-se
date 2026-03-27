package eu.darken.sdmse.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object RegexSerializer : KSerializer<Regex> {

    @Serializable
    private data class Surrogate(
        val pattern: String,
        val options: Set<Option>,
    ) {
        @Serializable
        enum class Option {
            @SerialName("IGNORE_CASE") IGNORE_CASE,
            @SerialName("MULTILINE") MULTILINE,
            @SerialName("LITERAL") LITERAL,
            @SerialName("UNIX_LINES") UNIX_LINES,
            @SerialName("COMMENTS") COMMENTS,
            @SerialName("DOT_MATCHES_ALL") DOT_MATCHES_ALL,
            @SerialName("CANON_EQ") CANON_EQ,
            ;
        }
    }

    override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Regex) {
        val surrogate = Surrogate(
            pattern = value.pattern,
            options = value.options.map { it.toSurrogateOption() }.toSet(),
        )
        encoder.encodeSerializableValue(Surrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Regex {
        val surrogate = decoder.decodeSerializableValue(Surrogate.serializer())
        return Regex(
            pattern = surrogate.pattern,
            options = surrogate.options.map { it.toRegexOption() }.toSet(),
        )
    }

    private fun RegexOption.toSurrogateOption() = when (this) {
        RegexOption.IGNORE_CASE -> Surrogate.Option.IGNORE_CASE
        RegexOption.MULTILINE -> Surrogate.Option.MULTILINE
        RegexOption.LITERAL -> Surrogate.Option.LITERAL
        RegexOption.UNIX_LINES -> Surrogate.Option.UNIX_LINES
        RegexOption.COMMENTS -> Surrogate.Option.COMMENTS
        RegexOption.DOT_MATCHES_ALL -> Surrogate.Option.DOT_MATCHES_ALL
        RegexOption.CANON_EQ -> Surrogate.Option.CANON_EQ
    }

    private fun Surrogate.Option.toRegexOption() = when (this) {
        Surrogate.Option.IGNORE_CASE -> RegexOption.IGNORE_CASE
        Surrogate.Option.MULTILINE -> RegexOption.MULTILINE
        Surrogate.Option.LITERAL -> RegexOption.LITERAL
        Surrogate.Option.UNIX_LINES -> RegexOption.UNIX_LINES
        Surrogate.Option.COMMENTS -> RegexOption.COMMENTS
        Surrogate.Option.DOT_MATCHES_ALL -> RegexOption.DOT_MATCHES_ALL
        Surrogate.Option.CANON_EQ -> RegexOption.CANON_EQ
    }
}
