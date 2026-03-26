package eu.darken.sdmse.common.serialization

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import android.net.Uri
import java.io.File

object APathSerializer : KSerializer<APath> {

    @Serializable
    private data class Surrogate(
        val pathType: APath.PathType,
        val path: String? = null,
        val treeRoot: String? = null,
        val segments: List<String>? = null,
    )

    override val descriptor: SerialDescriptor = SerialDescriptor(
        "eu.darken.sdmse.common.files.APath",
        Surrogate.serializer().descriptor,
    )

    override fun serialize(encoder: Encoder, value: APath) {
        val surrogate = when (value) {
            is RawPath -> Surrogate(pathType = APath.PathType.RAW, path = value.path)
            is LocalPath -> Surrogate(pathType = APath.PathType.LOCAL, path = value.file.path)
            is SAFPath -> Surrogate(pathType = APath.PathType.SAF, treeRoot = value.treeRoot, segments = value.segments)
            else -> throw SerializationException("Unknown APath type: ${value::class}")
        }
        encoder.encodeSerializableValue(Surrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): APath {
        val surrogate = decoder.decodeSerializableValue(Surrogate.serializer())
        return when (surrogate.pathType) {
            APath.PathType.RAW -> RawPath(
                surrogate.path ?: throw SerializationException("RAW path missing 'path' field"),
            )
            APath.PathType.LOCAL -> LocalPath(
                File(surrogate.path ?: throw SerializationException("LOCAL path missing 'path' field")),
            )
            APath.PathType.SAF -> {
                val treeRoot = surrogate.treeRoot ?: throw SerializationException("SAF path missing 'treeRoot' field")
                val uri = Uri.parse(treeRoot)
                if (uri.scheme != "content") throw SerializationException("SAF treeRoot must be a content:// URI, was: ${uri.scheme}")
                SAFPath(
                    treeRoot,
                    surrogate.segments ?: throw SerializationException("SAF path missing 'segments' field"),
                )
            }
        }
    }
}
