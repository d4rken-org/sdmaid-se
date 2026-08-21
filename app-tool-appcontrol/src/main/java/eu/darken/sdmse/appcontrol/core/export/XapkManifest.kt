package eu.darken.sdmse.appcontrol.core.export

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The `manifest.json` that sits next to the APKs inside an XAPK archive.
 *
 * Whether a field travels as a JSON string or as a JSON number is decided by the
 * [DecimalStringLong] / [DecimalStringInt] annotations below: removing one from a property makes
 * that property a JSON number, adding one makes it a decimal string.
 */
@Serializable
data class XapkManifest(
    @SerialName("xapk_version") val xapkVersion: Int = XAPK_VERSION,
    @SerialName("package_name") val packageName: String,
    @SerialName("name") val name: String,
    @SerialName("version_code") @Serializable(with = DecimalStringLong::class) val versionCode: Long,
    @SerialName("version_name") val versionName: String,
    @SerialName("min_sdk_version") @Serializable(with = DecimalStringInt::class) val minSdkVersion: Int,
    @SerialName("target_sdk_version") @Serializable(with = DecimalStringInt::class) val targetSdkVersion: Int,
    @SerialName("total_size") val totalSize: Long,
    @SerialName("split_apks") val splitApks: List<SplitApk>,
    @SerialName("split_configs") val splitConfigs: List<String>,
) {

    @Serializable
    data class SplitApk(
        @SerialName("file") val file: String,
        @SerialName("id") val id: String,
    )

    companion object {
        const val XAPK_VERSION = 2
        const val ID_BASE = "base"
    }
}

private object DecimalStringLong : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DecimalStringLong", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Long = decoder.decodeString().toLong()
}

private object DecimalStringInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DecimalStringInt", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Int = decoder.decodeString().toInt()
}
