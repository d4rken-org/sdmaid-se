package eu.darken.sdmse.common.parcel

import android.os.Parcel
import android.os.Parcelable

inline fun <reified T> T.marshall(): ByteArray where T : Parcelable = Parcel.obtain().use {
    writeParcelable(this@marshall, 0)
    marshall()
}

inline fun <reified T> ByteArray.unmarshall(): T where T : Any, T : Parcelable = Parcel.obtain().use {
    unmarshall(this@unmarshall, 0, size)
    setDataPosition(0)
    readParcelable(T::class.java.classLoader)!!
}

inline fun <reified T> T.forceParcel(): T where T : Parcelable {
    val bytes = this.marshall()
    return bytes.unmarshall()
}

inline fun <reified T> Parcel.use(action: Parcel.() -> T): T = try {
    action()
} finally {
    recycle()
}
