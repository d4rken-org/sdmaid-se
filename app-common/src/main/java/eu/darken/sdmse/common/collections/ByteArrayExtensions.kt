package eu.darken.sdmse.common.collections

import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.util.BitSet

fun Byte.toHex(): String = String.format("%02X", this)
fun Byte.isBitSet(pos: Int): Boolean = BitSet.valueOf(arrayOf(this).toByteArray()).get(pos)

fun ByteArray.toHex(): String = this.joinToString(separator = "") { String.format("%02X", it) }

fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()

fun Short.toByteArray(): ByteArray = ByteBuffer.allocate(Short.SIZE_BYTES).putShort(this).array()

fun String.toByteString() = toByteArray().toByteString()