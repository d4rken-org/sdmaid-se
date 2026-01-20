package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.PathException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup

sealed class LocalPathLookupResult : Parcelable {

    data class Success(
        val lookup: LocalPathLookup
    ) : LocalPathLookupResult() {
        @Suppress("DEPRECATION")
        constructor(parcel: Parcel) : this(
            parcel.readParcelable(LocalPathLookup::class.java.classLoader)!!
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(lookup, flags)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Success> {
            override fun createFromParcel(parcel: Parcel): Success = Success(parcel)
            override fun newArray(size: Int): Array<Success?> = arrayOfNulls(size)
        }
    }

    data class Error(
        val exceptionClass: String,
        val message: String?,
        val pathString: String? = null
    ) : LocalPathLookupResult() {
        constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString(),
            parcel.readString()
        )

        constructor(exception: Exception) : this(
            exception::class.java.name,
            exception.message,
            (exception as? PathException)?.path?.path
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(exceptionClass)
            parcel.writeString(message)
            parcel.writeString(pathString)
        }

        override fun describeContents(): Int = 0

        fun toException(): Exception {
            return try {
                val clazz = Class.forName(exceptionClass)
                when {
                    PathException::class.java.isAssignableFrom(clazz) -> {
                        // PathException and its subclasses require (message, path, cause) constructor
                        val path = pathString?.let { LocalPath.build(it) }
                        clazz
                            .getConstructor(String::class.java, APath::class.java, Throwable::class.java)
                            .newInstance(message, path, null) as Exception
                    }

                    else -> {
                        // Try standard Exception constructor
                        clazz
                            .asSubclass(Exception::class.java)
                            .getConstructor(String::class.java)
                            .newInstance(message)
                    }
                }
            } catch (_: Exception) {
                // Fallback to generic exception if reconstruction fails
                Exception("$exceptionClass: $message")
            }
        }

        companion object CREATOR : Parcelable.Creator<Error> {
            override fun createFromParcel(parcel: Parcel): Error = Error(parcel)
            override fun newArray(size: Int): Array<Error?> = arrayOfNulls(size)
        }
    }

    companion object CREATOR : Parcelable.Creator<LocalPathLookupResult> {
        override fun createFromParcel(parcel: Parcel): LocalPathLookupResult {
            return when (parcel.readInt()) {
                0 -> Success.createFromParcel(parcel)
                1 -> Error.createFromParcel(parcel)
                else -> throw IllegalArgumentException("Unknown result type")
            }
        }

        override fun newArray(size: Int): Array<LocalPathLookupResult?> = arrayOfNulls(size)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        when (this) {
            is Success -> {
                parcel.writeInt(0)
                writeToParcel(parcel, flags)
            }

            is Error -> {
                parcel.writeInt(1)
                writeToParcel(parcel, flags)
            }
        }
    }
}