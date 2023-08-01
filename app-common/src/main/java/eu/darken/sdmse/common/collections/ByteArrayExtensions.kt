package eu.darken.sdmse.common.collections

import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.util.BitSet

fun Byte.toHex(): String = String.format("%02X", this)
fun UByte.toHex(): String = this.toByte().toHex()

val Byte.upperNibble get() = (this.toInt() shr 4 and 0b1111).toShort()
val Byte.lowerNibble get() = (this.toInt() and 0b1111).toShort()
val UByte.upperNibble get() = (this.toInt() shr 4 and 0b1111).toUShort()
val UByte.lowerNibble get() = (this.toInt() and 0b1111).toUShort()

fun Byte.isBitSet(pos: Int): Boolean = BitSet.valueOf(arrayOf(this).toByteArray()).get(pos)
fun UByte.isBitSet(pos: Int): Boolean = this.toByte().isBitSet(pos)

fun Short.isBitSet(pos: Int): Boolean = this.toByte().isBitSet(pos)
fun UShort.isBitSet(pos: Int): Boolean = this.toShort().isBitSet(pos)

fun UShort.toBinaryString(): String = Integer.toBinaryString(this.toInt()).padStart(4, '0')
fun UByte.toBinaryString(): String = Integer.toBinaryString(this.toInt()).padStart(8, '0')

fun ByteArray.toHex(): String = this.joinToString(separator = "") { String.format("%02X", it) }
fun UByteArray.toHex(): String = this.joinToString(separator = "") { String.format("%02X", it.toByte()) }

fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()

fun Short.toByteArray(): ByteArray = ByteBuffer.allocate(Short.SIZE_BYTES).putShort(this).array()

fun Int.toUByteArray(): UByteArray = toByteArray().toUByteArray()

infix fun UShort.shl(bitCount: Int): UShort = (this.toUInt() shl bitCount).toUShort()
infix fun UShort.shr(bitCount: Int): UShort = (this.toUInt() shr bitCount).toUShort()

infix fun UByte.shl(bitCount: Int): UByte = (this.toUInt() shl bitCount).toUByte()
infix fun UByte.shr(bitCount: Int): UByte = (this.toUInt() shr bitCount).toUByte()

fun UByte.toBigEndianUShort(): UShort = this.toUShort() shl 8
fun UByte.toBigEndianUInt(): UInt = this.toUInt() shl 24

fun String.toByteString() = toByteArray().toByteString()