package eu.darken.sdmse.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File

object FileSerializer : KSerializer<File> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.io.File", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: File) = encoder.encodeString(value.path)
    override fun deserialize(decoder: Decoder): File = File(decoder.decodeString())
}
