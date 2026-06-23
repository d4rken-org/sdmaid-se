package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.PathException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended

sealed class LocalPathLookupExtendedResult : Parcelable {

    data class Success(
        val lookup: LocalPathLookupExtended
    ) : LocalPathLookupExtendedResult() {
        @Suppress("DEPRECATION")
        constructor(parcel: Parcel) : this(
            parcel.readParcelable(LocalPathLookupExtended::class.java.classLoader)!!
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
    ) : LocalPathLookupExtendedResult() {
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

    /**
     * Terminal marker the host emits after the source flow completed successfully.
     * Its presence lets the consumer tell a clean end-of-stream apart from a truncated one
     * (e.g. the privileged host process dying mid-stream) — both look identical at EOF and would
     * otherwise silently yield a partial directory listing.
     */
    object Complete : LocalPathLookupExtendedResult() {
        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            // No payload; the marker itself is the signal.
        }

        @JvmField
        val CREATOR = object : Parcelable.Creator<Complete> {
            override fun createFromParcel(parcel: Parcel): Complete = Complete
            override fun newArray(size: Int): Array<Complete?> = arrayOfNulls(size)
        }
    }

    companion object CREATOR : Parcelable.Creator<LocalPathLookupExtendedResult> {
        override fun createFromParcel(parcel: Parcel): LocalPathLookupExtendedResult {
            return when (parcel.readInt()) {
                0 -> Success.createFromParcel(parcel)
                1 -> Error.createFromParcel(parcel)
                2 -> Complete
                else -> throw IllegalArgumentException("Unknown result type")
            }
        }

        override fun newArray(size: Int): Array<LocalPathLookupExtendedResult?> = arrayOfNulls(size)
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

            Complete -> {
                parcel.writeInt(2)
            }
        }
    }
}
