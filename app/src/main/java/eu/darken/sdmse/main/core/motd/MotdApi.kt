package eu.darken.sdmse.main.core.motd

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface MotdApi {

    @JsonClass(generateAdapter = true)
    data class DirectoryContent(
        @Json(name = "name") val name: String,
        @Json(name = "path") val path: String,
        @Json(name = "type") val type: String,
        @Json(name = "download_url") val downloadUrl: String?,
    )

    @GET("repos/d4rken-org/sdmaid-se/contents/{path}")
    suspend fun listMotds(
        @Path("path") path: String,
        @Query("ref") branch: String,
    ): List<DirectoryContent>

    @GET
    suspend fun getMotd(@Url url: String): Motd

}