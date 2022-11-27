package eu.darken.sdmse.common.files.core.local

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.TypeMissMatchException
import eu.darken.sdmse.common.files.core.APath
import kotlinx.parcelize.IgnoredOnParcel
import java.io.File


@Keep
@JsonClass(generateAdapter = true)
data class LocalPath(
    val file: File
) : eu.darken.sdmse.common.files.core.APath {

    override var pathType: eu.darken.sdmse.common.files.core.APath.PathType
        get() = eu.darken.sdmse.common.files.core.APath.PathType.LOCAL
        set(value) {
            TypeMissMatchException.check(value, pathType)
        }

    @IgnoredOnParcel
    override val path: String = file.path

    @IgnoredOnParcel
    override val name: String = file.name

    @IgnoredOnParcel
    override val segments: List<String> = file.parentsInclusive.map { it.name }.toList()

    override fun child(vararg segments: String): LocalPath {
        return build(this.file, *segments)
    }

    override fun toString(): String = "LocalPath(file=$file)"

    constructor(parcel: Parcel) : this(File(parcel.readString()))

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(file.path)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<LocalPath> {
            override fun createFromParcel(parcel: Parcel): LocalPath {
                return LocalPath(parcel)
            }

            override fun newArray(size: Int): Array<LocalPath?> {
                return arrayOfNulls(size)
            }
        }

        fun build(base: LocalPath, vararg crumbs: String): LocalPath {
            return build(base.path, *crumbs)
        }

        fun build(base: File, vararg crumbs: String): LocalPath {
            return build(base.path, *crumbs)
        }

        fun build(vararg crumbs: String): LocalPath {
            var compacter = File(crumbs[0])
            for (i in 1 until crumbs.size) {
                compacter = File(compacter, crumbs[i])
            }
            return build(compacter)
        }

        fun build(file: File): LocalPath {
            return LocalPath(file)
        }
    }


}