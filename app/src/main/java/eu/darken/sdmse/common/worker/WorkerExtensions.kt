package eu.darken.sdmse.common.worker

import android.os.Parcel
import android.os.Parcelable
import androidx.work.Data

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Parcelable> Data.getParcelable(key: String): T? {
    val parcel = Parcel.obtain()
    try {
        val bytes = getByteArray(key) ?: return null
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        val creator = T::class.java.getField("CREATOR").get(null) as Parcelable.Creator<T>
        return creator.createFromParcel(parcel)
    } finally {
        parcel.recycle()
    }
}


fun Data.Builder.putParcelable(key: String, parcelable: Parcelable): Data.Builder {
    val parcel = Parcel.obtain()
    try {
        parcelable.writeToParcel(parcel, 0)
        putByteArray(key, parcel.marshall())
    } finally {
        parcel.recycle()
    }
    return this
}
