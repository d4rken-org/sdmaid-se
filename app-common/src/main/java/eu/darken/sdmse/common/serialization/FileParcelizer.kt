package eu.darken.sdmse.common.serialization

import android.os.Parcel
import kotlinx.parcelize.Parceler
import java.io.File

object FileParcelizer : Parceler<File> {
    override fun create(parcel: Parcel) = File(parcel.readString()!!)

    override fun File.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this.path)
    }
}