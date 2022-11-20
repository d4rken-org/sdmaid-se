package eu.darken.sdmse.common.serialization

import android.os.Parcel
import kotlinx.parcelize.Parceler
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

object ByteStringParcelizer : Parceler<ByteString> {
    override fun create(parcel: Parcel) = parcel.readString()?.decodeBase64()!!

    override fun ByteString.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this.base64())
    }
}