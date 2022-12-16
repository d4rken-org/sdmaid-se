package eu.darken.sdmse.common.files.core.local

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.TypeMissMatchException
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.serialization.FileParcelizer
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.io.File


@Keep
@JsonClass(generateAdapter = true)
@Parcelize
@TypeParceler<File, FileParcelizer>()
data class LocalPath(
    val file: File
) : APath {

    override var pathType: APath.PathType
        get() = APath.PathType.LOCAL
        set(value) {
            TypeMissMatchException.check(value, pathType)
        }

    @IgnoredOnParcel override val path: String
        get() = file.path

    @IgnoredOnParcel override val name: String
        get() = file.name

    @IgnoredOnParcel
    override val segments: List<String>
        get() = file.parentsInclusive.map { it.name }.toList()

    override fun child(vararg segments: String): LocalPath = build(this.file, *segments)

    override fun toString(): String = "LocalPath(file=$file)"

    override fun describeContents(): Int = 0

    fun parent(): LocalPath? {
        val raw = segments.dropLast(1)
        return if (raw.isEmpty()) null else build(*raw.toTypedArray())
    }

    companion object {
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