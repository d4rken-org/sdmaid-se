package eu.darken.sdmse.common.collections

import okio.*


fun ByteString.fromGzip(): ByteString = Buffer().use { buf ->
    buf.write(this)
    (buf as Source).gzip().buffer().use { it.readByteString() }
}

fun ByteString.toGzip(): ByteString = Buffer().use { buf ->
    (buf as Sink).gzip().buffer().use { it.write(this) }
    buf.use { it.readByteString() }
}