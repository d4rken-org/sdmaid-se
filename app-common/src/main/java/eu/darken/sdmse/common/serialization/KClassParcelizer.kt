package eu.darken.sdmse.common.serialization

import android.os.Parcel
import kotlinx.parcelize.Parceler
import kotlin.reflect.KClass

object KClassParcelizer : Parceler<KClass<*>> {
    override fun create(parcel: Parcel) = (parcel.readValue(Class::class.java.classLoader) as Class<*>).kotlin

    override fun KClass<*>.write(parcel: Parcel, flags: Int) {
        parcel.writeValue(this.java)
    }
}