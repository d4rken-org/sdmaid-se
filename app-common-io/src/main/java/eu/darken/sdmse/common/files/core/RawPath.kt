package eu.darken.sdmse.common.files.core

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.TypeMissMatchException
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize @Keep
@JsonClass(generateAdapter = true)
data class RawPath(
    override val path: String
) : eu.darken.sdmse.common.files.core.APath {

    override var pathType: eu.darken.sdmse.common.files.core.APath.PathType
        get() = eu.darken.sdmse.common.files.core.APath.PathType.RAW
        set(value) {
            TypeMissMatchException.check(value, pathType)
        }

    override val name: String
        get() = path.substringAfterLast(File.separatorChar)

    override val segments: List<String>
        get() = throw NotImplementedError()

    override fun child(vararg segments: String): eu.darken.sdmse.common.files.core.APath {
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