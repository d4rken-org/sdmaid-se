package eu.darken.sdmse.common.files

import android.os.Parcelable
import androidx.annotation.Keep
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.serialization.ValueBasedPolyJsonAdapterFactory

@Keep
interface APath : Parcelable {
    val path: String
    val name: String
    val pathType: PathType

    val userReadablePath: CaString
        get() = path.toCaString()
    val userReadableName: CaString
        get() = name.toCaString()

    val segments: Segments

    fun child(vararg segments: String): APath

    @Keep
    enum class PathType {
        RAW, LOCAL, SAF
    }

    companion object {
        val MOSHI_FACTORY: ValueBasedPolyJsonAdapterFactory<APath> =
            ValueBasedPolyJsonAdapterFactory.of(APath::class.java, "pathType")
                .withSubtype(RawPath::class.java, PathType.RAW.name)
                .withSubtype(LocalPath::class.java, PathType.LOCAL.name)
                .withSubtype(SAFPath::class.java, PathType.SAF.name)
                .skipLabelSerialization()
    }

}