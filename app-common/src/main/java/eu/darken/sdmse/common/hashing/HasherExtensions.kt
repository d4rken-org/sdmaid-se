package eu.darken.sdmse.common.hashing

import okio.Buffer
import okio.Source


fun ByteArray.hash(type: Hasher.Type) = Hasher(type).calc(Buffer().write(this))

fun String.hash(type: Hasher.Type) = this.toByteArray().hash(type)

fun Source.hash(type: Hasher.Type) = Hasher(type).calc(this)
