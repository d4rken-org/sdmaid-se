package eu.darken.sdmse.main.core.motd

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface MotdApi {

    @Serializable
    data class DirectoryContent(
        @SerialName("name") val name: String,
        @SerialName("type") val type: String,
        @SerialName("download_url") val downloadUrl: String?,
    )

    @GET("repos/d4rken-org/sdmaid-se/contents/{path}")
    suspend fun listMotds(
        @Path("path") path: String,
        @Query("ref") branch: String,
    ): List<DirectoryContent>

    @GET
    suspend fun getMotd(@Url url: String): Motd

}
