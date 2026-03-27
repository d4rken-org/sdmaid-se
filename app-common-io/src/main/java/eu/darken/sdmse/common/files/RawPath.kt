package eu.darken.sdmse.common.files

import androidx.annotation.Keep
import eu.darken.sdmse.common.TypeMissMatchException
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
@Parcelize
@Keep
data class RawPath(
    @SerialName("path") override val path: String,
) : APath {

    override var pathType: APath.PathType
        get() = APath.PathType.RAW
        set(value) {
            TypeMissMatchException.check(value, pathType)
        }

    override val name: String
        get() = path.substringAfterLast(File.separatorChar)

    override val segments: List<String>
        get() = throw NotImplementedError()

    override fun child(vararg segments: String): APath {
        throw NotImplementedError()
    }

    companion object {
        fun build(base: File, vararg crumbs: String): RawPath = build(base.path, *crumbs)

        fun build(vararg crumbs: String): RawPath {
            var compacter = File(crumbs[0])
            for (i in 1 until crumbs.size) {
                compacter = File(compacter, crumbs[i])
            }
            return RawPath(compacter.path)
        }
    }
}