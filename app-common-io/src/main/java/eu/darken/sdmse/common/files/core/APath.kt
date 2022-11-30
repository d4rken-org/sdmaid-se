package eu.darken.sdmse.common.files.core

import android.content.Context
import android.os.Parcelable
import androidx.annotation.Keep
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.saf.SAFPath
import eu.darken.sdmse.common.serialization.MyPolymorphicJsonAdapterFactory

@Keep
interface APath : Parcelable {
    val path: String
    val name: String
    val pathType: PathType

    // TODO use AString
    fun userReadablePath(context: Context) = path
    fun userReadableName(context: Context) = name

    val segments: List<String>
    fun child(vararg segments: String): APath

    @Keep
    enum class PathType {
        RAW, LOCAL, SAF
    }

    companion object {
        val MOSHI_FACTORY: MyPolymorphicJsonAdapterFactory<APath> =
            MyPolymorphicJsonAdapterFactory.of(APath::class.java, "pathType")
                .withSubtype(RawPath::class.java, PathType.RAW.name)
                .withSubtype(LocalPath::class.java, PathType.LOCAL.name)
                .withSubtype(SAFPath::class.java, PathType.SAF.name)
                .skipLabelSerialization()
    }

}