package eu.darken.sdmse.common.shell.ipc

import android.os.Parcel
import android.os.Parcelable

sealed class ShellOpsStreamEvent : Parcelable {

    abstract val estimatedParcelSize: Int

    data class Stdout(val line: String) : ShellOpsStreamEvent() {
        constructor(parcel: Parcel) : this(parcel.readString()!!)

        override val estimatedParcelSize: Int get() = OVERHEAD + line.length * 2

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(line)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Stdout> {
            override fun createFromParcel(parcel: Parcel): Stdout = Stdout(parcel)
            override fun newArray(size: Int): Array<Stdout?> = arrayOfNulls(size)
        }
    }

    data class Stderr(val line: String) : ShellOpsStreamEvent() {
        constructor(parcel: Parcel) : this(parcel.readString()!!)

        override val estimatedParcelSize: Int get() = OVERHEAD + line.length * 2

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(line)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Stderr> {
            override fun createFromParcel(parcel: Parcel): Stderr = Stderr(parcel)
            override fun newArray(size: Int): Array<Stderr?> = arrayOfNulls(size)
        }
    }

    data class Exit(val exitCode: Int) : ShellOpsStreamEvent() {
        constructor(parcel: Parcel) : this(parcel.readInt())

        override val estimatedParcelSize: Int get() = OVERHEAD

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(exitCode)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Exit> {
            override fun createFromParcel(parcel: Parcel): Exit = Exit(parcel)
            override fun newArray(size: Int): Array<Exit?> = arrayOfNulls(size)
        }
    }

    data class Error(val message: String) : ShellOpsStreamEvent() {
        constructor(parcel: Parcel) : this(parcel.readString()!!)

        override val estimatedParcelSize: Int get() = OVERHEAD + message.length * 2

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(message)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Error> {
            override fun createFromParcel(parcel: Parcel): Error = Error(parcel)
            override fun newArray(size: Int): Array<Error?> = arrayOfNulls(size)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        when (this) {
            is Stdout -> {
                parcel.writeInt(TYPE_STDOUT)
                writeToParcel(parcel, flags)
            }
            is Stderr -> {
                parcel.writeInt(TYPE_STDERR)
                writeToParcel(parcel, flags)
            }
            is Exit -> {
                parcel.writeInt(TYPE_EXIT)
                writeToParcel(parcel, flags)
            }
            is Error -> {
                parcel.writeInt(TYPE_ERROR)
                writeToParcel(parcel, flags)
            }
        }
    }

    companion object CREATOR : Parcelable.Creator<ShellOpsStreamEvent> {
        private const val TYPE_STDOUT = 0
        private const val TYPE_STDERR = 1
        private const val TYPE_EXIT = 2
        private const val TYPE_ERROR = 3

        private const val OVERHEAD = 16

        override fun createFromParcel(parcel: Parcel): ShellOpsStreamEvent = when (parcel.readInt()) {
            TYPE_STDOUT -> Stdout.createFromParcel(parcel)
            TYPE_STDERR -> Stderr.createFromParcel(parcel)
            TYPE_EXIT -> Exit.createFromParcel(parcel)
            TYPE_ERROR -> Error.createFromParcel(parcel)
            else -> throw IllegalArgumentException("Unknown ShellOpsStreamEvent type")
        }

        override fun newArray(size: Int): Array<ShellOpsStreamEvent?> = arrayOfNulls(size)
    }
}
