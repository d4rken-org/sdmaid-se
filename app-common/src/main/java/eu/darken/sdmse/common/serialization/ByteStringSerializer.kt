package eu.darken.sdmse.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

object ByteStringSerializer : KSerializer<ByteString> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("okio.ByteString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteString) = encoder.encodeString(value.base64())
    override fun deserialize(decoder: Decoder): ByteString = decoder.decodeString().decodeBase64()!!
}
