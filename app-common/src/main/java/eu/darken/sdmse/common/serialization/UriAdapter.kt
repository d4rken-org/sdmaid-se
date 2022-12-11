package eu.darken.sdmse.common.serialization

import android.net.Uri
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

class UriAdapter {
    @ToJson
    fun toJson(uri: Uri): String = uri.toString()

    @FromJson
    fun fromJson(uriString: String): Uri = Uri.parse(uriString)
}
